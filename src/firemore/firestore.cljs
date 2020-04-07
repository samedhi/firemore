(ns firemore.firestore
  (:require
   [cljs.core.async :as async]
   [clojure.string :as string]
   [firemore.config :as config]
   [firemore.firebase :as firebase])
  (:require-macros
   [cljs.core.async.macros :refer [go-loop go]]
   [firemore.firestore-macros :refer [transact-db!]]))

(def FB firebase/FB)

(def ^:dynamic *transaction* nil)

(def ^:dynamic *transaction-unwritten-docs* nil)

(def server-timestamp (.serverTimestamp js/firebase.firestore.FieldValue))

(defn disj-reference
  "Remove 'reference from list of items that must be written in transaction"
  [reference]
  (swap! *transaction-unwritten-docs* disj reference))

(defn ref
  "Convert a firemore reference to a firebase reference"
  ([path] (ref FB path))
  ([fb path]
   (loop [[p & ps] path
          collection? true
          obj (firebase/db fb)]
     (let [new-obj (if collection?
                     (.collection obj (name p))
                     (.doc obj p))]
       (if (empty? ps)
         new-obj
         (recur ps (not collection?) new-obj))))))

(defn str->keywordize
  "If s begins with ':' then convert into a keyword, else returns 's"
  {:pre [(string? s)]}
  [s]
  (if (= (subs s 0 1) ":")
    (as-> s $
      (subs $ 1)
      (string/split $ "/")
      (apply keyword $))
    s))

(defn keywordize->str
  "Mirror function for str->keywordize"
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

(defn replace-timestamp
  "Replace `config/TIMESTAMP (keyword) with firebase Server Timestamp"
  [m]
  (reduce-kv
   (fn [m k v]
     (if (= v config/TIMESTAMP)
       (assoc m k server-timestamp)
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

(defn closer [c]
  (async/close! c))

(defn promise->chan
  ([fx]
   (promise->chan
    fx
    (fn [c value]
      (when (some? value)
        (async/put! c value)))))
  ([fx on-success]
   (promise->chan
    fx
    on-success
    (fn [c error]
      (js/console.log error)
      (async/put! c error))))
  ([fx on-success on-failure]
   (if *transaction*
     (fx)
     (let [c (async/chan)]
       (..
        (fx)
        (then (partial on-success c))
        (catch (partial on-failure c))
        (then (partial closer c)))
       c))))

(defn promise->mchan [fx]
  (promise->chan
   (fn [c value] (async/put! c {:success true  :value value}))
   (fn [c error] (async/put! c {:success false :error error}))))

(def default-options
  {:fb FB})

(defn merge-default-options [options]
  (merge default-options options))

(defn set-db!
  ([reference value] (set-db! reference value nil))
  ([reference value options]
   (let [{:keys [fb] :or {fb FB}} (merge-default-options options)
         {:keys [ref js-value]} (shared-db fb reference value)]
     (promise->chan
      (if *transaction*
        #(do (.set *transaction* ref js-value)
             (disj-reference reference))
        #(.set ref js-value))))))

(defn add-db!
  ([reference value] (add-db! reference value nil))
  ([reference value options]
   (let [{:keys [fb]} (merge-default-options options)
         {:keys [ref js-value]} (shared-db fb reference value)]
     (promise->chan
      (if *transaction*
        #(do (.add *transaction* ref js-value)
             (disj-reference reference))
        #(.add ref js-value))
      (fn [c docRef]
        (async/put! c {:id (.-id docRef)})
        (async/close! c))))))

(defn update-db!
  ([reference value] (update-db! reference value nil))
  ([reference value options]
   (let [{:keys [fb]} (merge-default-options options)
         {:keys [ref js-value]} (shared-db fb reference value)]
     (promise->chan
      (if *transaction*
        #(do (.update *transaction* ref js-value)
             (disj-reference reference))
        #(.update ref js-value))))))

(defn delete-db!
  ([reference] (delete-db! reference nil))
  ([reference options]
   (let [{:keys [fb]} (merge-default-options options)
         {:keys [ref]} (shared-db fb reference nil)]
     (promise->chan
      (if *transaction*
        #(do (.delete *transaction* ref)
             (disj-reference reference))
        #(.delete ref))))))

(defn add-where-to-ref [ref query]
  (reduce
   (fn [ref [k op v]]
     (.where ref (str k) op (if (coll? v) (clj->js v) v)))
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

(defn doc-upgrader [doc]
  (if-let [exists? (.-exists doc)]
    (with-meta
      (clojurify (.data doc))
      {:id (.-id doc)
       :pending? (.. doc -metadata -hasPendingWrites)})
    config/NO_DOCUMENT))

(defn get-db
  ([reference]
   (get-db reference nil))
  ([reference options]
   (let [{:keys [fb]} (merge-default-options options)
         {:keys [ref query]} (shared-db fb reference)]
     (if query
       (promise->chan
        #(.get (filter-by-query ref query))
        (fn [c snapshot]
          (let [a (atom [])]
            (.forEach snapshot #(->> % doc-upgrader (swap! a conj)))
            (async/put! c @a))))
       (promise->chan
        #(.get ref)
        (fn [c doc]
          (->> doc doc-upgrader (async/put! c))))))))

(defn snapshot-handler [collection? c snapshot]
  (async/put!
   c
   (if collection?
     (let [a (atom [])]
       (.forEach snapshot #(->> % doc-upgrader (swap! a conj)))
       @a)
     (doc-upgrader snapshot))))

(defn snapshot-listen-options->js [options]
  (let [{:keys [include-metadata-changes]} options]
    (clj->js
     (merge {}
            (when include-metadata-changes
              {:includeMetadataChanges true})))))

(defn listen
  ([reference] (listen reference nil))
  ([reference options]
   (let [{:keys [fb]} (merge-default-options options)
         {:keys [ref query]} (shared-db fb reference nil)
         c (async/chan)
         collection? (some? query)
         handler (partial snapshot-handler collection? c)
         unsubscribe (.onSnapshot (if collection?
                                    (filter-by-query ref query)
                                    ref)
                                  (snapshot-listen-options->js options)
                                  handler)
         unsubscribe-fx #(do (async/close! c) (unsubscribe))]
     {:c c :unsubscribe unsubscribe-fx})))

(defn unlisten-db [{:keys [unsubscribe]}]
  (unsubscribe))
