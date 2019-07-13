(ns firemore.firestore
  (:require
   [cljs.core.async :as async]
   [clojure.string :as string]
   [firemore.config :as config])
  (:require-macros
   [cljs.core.async.macros :refer [go-loop go]]))

(def OPTS
  #js {:apiKey  config/FIREBASE_API_KEY
       :authDomain config/FIREBASE_AUTH_DOMAIN
       :projectId config/FIREBASE_PROJECT_ID})

(defonce FB (js/firebase.initializeApp OPTS))

(defn db [firebase]
  (.firestore firebase))

(defn ref [fb path]
  (let [a (-> fb db atom)]
    (loop [[col doc & rs] path]
      (reset! a (.collection @a col))
      (when (some? doc)
        (reset! a (.doc @a doc)))
      (when (some? rs) (recur rs)))
    @a))

(defn str->keywordize
  {:pre [(string? s)]}
  [s]
  (if (= (subs s 0 1) ":")
    (as-> s $
      (subs $ 1)
      (string/split $ "/") 
      (apply keyword $))
    s))

(defn keywordize->str
  {:pre [(keyword? k)]}
  [k]
  (str k))

(defn jsonify [value]
  (clj->js value :keyword-fn keywordize->str))

(defn clojurify [value]
  (reduce-kv
   #(assoc %1 (str->keywordize %2) %3)
   {}
   (js->clj value)))

(defn replace-timestamp [m]
  (reduce-kv
   (fn [m k v]
     (if (= v config/TIMESTAMP)
       (let [ts (.serverTimestamp js/firebase.firestore.FieldValue)]
         (assoc m k ts))
       m))
   m
   m))

(defn build-path [fb path]
  (merge
   {:ref (ref fb path)
    :path path}
   (when (-> path count even?)
     {:id (peek path)})))

(defn build-query [fb path query]
  (throw (js/Error "Haven't implemented this!")))

(defn shared-db
  ([fb reference]
   {:pre [(vector? reference)]}
   (if (-> reference peek map?)
     (build-query fb (pop reference) (peek reference))
     (build-path fb reference)))
  ([fb reference value]
   (merge
    (shared-db fb reference)
    {:js-value (-> value replace-timestamp jsonify)})))

(defn promise->chan [fx]
  (let [c (async/chan)]
    (..
     (fx)
     (then
      (fn [docRef]
        (async/close! c)))
     (catch
         (fn [error]
           (async/put! c error)
           (async/close! c))))
    c))

(defn set-db!
  ([reference value] (set-db! FB reference value))
  ([fb reference value]
   (let [{:keys [id ref js-value]} (shared-db fb reference value)]
     (promise->chan #(.set ref js-value)))))

(defn add-db!
  ([reference value] (add-db! FB reference value))
  ([fb reference value]
   (let [{:keys [id ref js-value]} (shared-db fb reference value)
         c (async/chan)]
     (..
      (.add ref js-value)
      (then
       (fn [docRef]
         (async/put! c {:id (.-id docRef)})
         (async/close! c)))
      (catch
          (fn [error]
            (async/put! c error)
            (async/close! c))))
     c)))

(defn update-db!
  ([reference value] (update-db! FB reference value))
  ([fb reference value]
   (let [{:keys [ref js-value]} (shared-db fb reference value)]
     (promise->chan #(.update ref js-value)))))

(defn delete-db!
  ([reference] (delete-db! FB reference))
  ([fb reference]
   (let [{:keys [ref]} (shared-db fb reference nil)]
     (promise->chan #(.delete ref)))))

(defn get-db
  ([reference] (get-db FB reference))
  ([fb reference]
   (let [{:keys [ref]} (shared-db fb reference nil)
         c (async/chan)]
     (.then (.get ref)
            (fn [doc]
              (some->> (.data doc) clojurify (async/put! c))
              (async/close! c)))
     c)))

(defn listen-db
  ([reference] (listen-db FB reference))
  ([fb reference]
   (let [{:keys [ref]} (shared-db fb reference nil)
         c (async/chan)
         unsubscribe (atom nil)
         fx (fn [doc]
              (let [data (clojurify (.data doc))]
                (when-not (true? (async/put! c data))
                  (@unsubscribe))))]
     (reset! unsubscribe (.onSnapshot ref fx))
     {:chan c :unsubscribe @unsubscribe})))

(defn unlisten-db [{:keys [chan unsubscribe]}]
  (unsubscribe)
  (async/close! chan))
