(ns firemore.firestore
  (:require
   [cljs.core.async :as async]
   [clojure.string :as string]
   [firemore.config :as config]
   [firemore.firebase :as firebase])
  (:require-macros
   [cljs.core.async.macros :refer [go-loop go]]))

(def FB firebase/FB)

(defn ref
  ([path] (ref FB path))
  ([fb path]
   (let [a (-> fb firebase/db atom)]
     (loop [[col doc & rs] path]
       (reset! a (.collection @a (name col)))
       (when (some? doc)
         (reset! a (.doc @a doc)))
       (when (some? rs) (recur rs)))
     @a)))

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

(defn clojurify [json-document]
  (reduce-kv
   #(assoc %1 (str->keywordize %2) %3)
   {}
   (js->clj json-document)))

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

(defn expand-where [where]
  (when where
    (let [[expression-1] where]
      (when-not (vector? expression-1)
        {:where [where]}))))

(defn convert-if-string [order-expression]
  (if (string? order-expression)
    [order-expression "asc"]
    order-expression))

(defn expand-order [order]
  (when order
    {:order (mapv convert-if-string order)}))

(defn expand-query [query]
  (let [{:keys [where order limit start-at start-after end-at end-before]} query]
    (merge
     query
     (expand-where where)
     (expand-order order))))

(defn build-query [fb path query]
  (merge
   (build-path fb path)
   {:query (expand-query query)}))

(defn shared-db
  ([fb reference]
   {:pre [(vector? reference)]}
   (cond
     (-> reference peek map?) (build-query fb (pop reference) (peek reference))
     (-> reference count odd?) (build-query fb reference {})
     :else (build-path fb reference)))
  ([fb reference value]
   (merge
    (shared-db fb reference)
    {:js-value (-> value replace-timestamp jsonify)})))

(defn promise->chan
  ([fx]
   (promise->chan
    fx
    (fn [c] (async/close! c))))
  ([fx on-success]
   (promise->chan
    fx
    on-success
    (fn [c error]
      (js/console.log error)
      (async/put! c error)
      (async/close! c))))
  ([fx on-success on-failure]
   (let [c (async/chan)]
     (..
      (fx)
      (then (partial on-success c))
      (catch (partial on-failure c)))
     c)))

(defn set-db!
  ([reference value] (set-db! FB reference value))
  ([fb reference value]
   (let [{:keys [id ref js-value]} (shared-db fb reference value)]
     (promise->chan #(.set ref js-value)))))

(defn add-db!
  ([reference value] (add-db! FB reference value))
  ([fb reference value]
   (let [{:keys [id ref js-value]} (shared-db fb reference value)]
     (promise->chan
      #(.add ref js-value)
      (fn [c docRef]
        (async/put! c {:id (.-id docRef)})
        (async/close! c))))))

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

(defn add-where-to-ref [ref query]
  (reduce
   (fn [ref [k op v]] (.where ref (str k) op v))
   ref
   (:where query)))

(defn add-order-to-ref [ref query]
  (reduce
   (fn [ref [k direction]] (.orderBy ref (str k) direction))
   ref
   (:order query)))

(defn add-limit-to-ref [ref query]
  (if-let [limit (:limit query)]
    (.limit ref limit)
    ref))

(defn filter-by-query [ref query]
  (-> ref
      (add-where-to-ref query)
      (add-order-to-ref query)
      (add-limit-to-ref query)))

(defn doc-upgrader
  ([doc] (doc-upgrader doc nil))
  ([doc removed?]
   (with-meta
     (clojurify (.data doc))
     {:id (.-id doc)
      :exist? (if removed? false (.-exists doc))
      :pending? (.. doc -metadata -hasPendingWrites)})))

(defn get-db
  ([reference]
   (get-db FB reference))
  ([fb reference]
   (let [{:keys [ref query]} (shared-db fb reference)]
     (if query
       (promise->chan
        #(.get (filter-by-query ref query))
        (fn [c snapshot]
          (let [a (atom [])]
            (.forEach snapshot #(->> %
                                     doc-upgrader
                                     (swap! a conj)))
            (async/put! c @a)
            (async/close! c))))
       (promise->chan
        #(.get ref)
        (fn [c doc]
          (->> doc doc-upgrader (async/put! c))
          (async/close! c)))))))

#_(async/go (let [c (get-db ["cities"])]
              (loop []
                (when-let [m (async/<! c)]
                  (println (pr-str m))
                  (recur)))))

;; TODO: This removed? argument is nonsense, but for some reason `exists` in the document
;; does not agree with "removed" from the change...
(defn doc-handler
  ([c doc] (doc-handler c doc nil))
  ([c doc removed?]
   (async/put!
    c
    (doc-upgrader doc removed?))))

(defn listen-db
  ([reference] (listen-db FB reference))
  ([fb reference]
   (let [{:keys [ref query]} (shared-db fb reference nil)
         c (async/chan)
         doc-fx (partial doc-handler c)
         fx (if query
              (fn [snapshot]
                (.forEach (.docChanges snapshot ;; TODO: #js {:includeMetadataChanges true}
                                       )
                          (fn [change]
                            (doc-fx (.-doc change)
                                    ;; TODO: More of the same nonsense
                                    (= "removed" (.-type change))))))
              doc-fx)
         unsubscribe (.onSnapshot (if query (filter-by-query ref query) ref)
                                  ;; TODO: #js {:includeMetadataChanges true}
                                  fx)
         unsubscribe-fx #(do (async/close! c) (unsubscribe))]
     {:c c :unsubscribe unsubscribe-fx})))

(defn listen-collection-db
  ([reference] (listen-collection-db FB reference))
  ([fb reference]
   (let [{:keys [ref query]} (shared-db fb reference nil)
         c (async/chan)
         fx (fn [snapshot]
              (let [a (atom [])]
                (.forEach snapshot #(->> %
                                         doc-upgrader
                                         (swap! a conj)))
                (async/put! c @a)))
         unsubscribe (.onSnapshot (filter-by-query ref query)
                                  #js {:includeMetadataChanges true}
                                  fx)
         unsubscribe-fx #(do (async/close! c) (unsubscribe))]
     {:c c :unsubscribe unsubscribe-fx})))

(defn unlisten-db [{:keys [unsubscribe]}]
  (unsubscribe))
