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

(defn split-keyword [k]
  (if (= (name k) "add")
    [(namespace k)]
    [(namespace k) (name k)]))

(defn ref [fb path]
  (let [a (-> fb db atom)]
    (loop [[[col doc] & rs] (map split-keyword path)]
      (reset! a (.collection @a col))
      (when (some? doc)
        (reset! a (.doc @a doc)))
      (when (some? rs) (recur rs)))
    @a))

(defn keywordize [s]
  (if (= (subs s 0 1) ":")
    (keyword (subs s 1))
    s))

(defn un-keywordize [kwd]
  (str ":" (name kwd)))

(defn jsonify [value]
  (clj->js value :keyword-fn un-keywordize))

(defn clojurify [value]
  (let [value (js->clj value)]
    (if (map? value)
      (reduce-kv
       #(assoc %1 (keywordize %2) (if (string? %3) (keywordize %3) %3))
       {}
       value)
      value)))

(defn replace-timestamp [m]
  (->> m
       (filter (fn [[_ v]] (= v :timestamp)))
       (map (fn [[k v]] [k (.serverTimestamp js/firebase.firestore.FieldValue)]))
       flatten
       (apply assoc m)))

(defn shared-db [fb path value]
  {:id (-> path peek name)
   :ref (ref fb path)
   :js-value (-> value replace-timestamp jsonify)})

(defn set-db! [fb path value]
  (let [{:keys [id ref js-value]} (shared-db fb path value)]
    (if (= id "add")
      (.add ref js-value)
      (.set ref js-value))))

(defn update-db! [fb path value]
  (let [{:keys [ref js-value]} (shared-db fb path value)]
    (.update ref js-value)))

(defn delete-db! [fb path]
  (let [{:keys [ref]} (shared-db fb path nil)]
    (.delete ref)))

(defn get-db! [fb path]
  (let [{:keys [ref]} (shared-db fb path nil)
        c (async/chan)]
    (.then (.get ref)
           (fn [doc]
             (->> (.data doc) clojurify (async/put! c))
             (async/close! c)))
    c))

(defn listen-db! [fb path]
  (let [{:keys [ref]} (shared-db fb path nil)
        c (async/chan)
        unsubscribe (atom nil)
        fx (fn [doc]
             (let [data (clojurify (.data doc))]
               (when-not (true? (async/put! c data))
                 (@unsubscribe))))]
    (reset! unsubscribe (.onSnapshot ref fx))
    {:chan c :unsubscribe @unsubscribe}))

(defn unlisten-db! [{:keys [chan unsubscribe]}]
  (unsubscribe)
  (async/close! chan))
