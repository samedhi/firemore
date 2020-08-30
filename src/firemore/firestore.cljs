(ns firemore.firestore
  (:require
   [cljs.core.async :as async]
   [clojure.set :as set]
   [clojure.string :as string]
   [firemore.config :as config]
   [firemore.firebase :as firebase])
  (:require-macros
   [cljs.core.async.macros :refer [go-loop go]]))

(def server-timestamp (.serverTimestamp js/firebase.firestore.FieldValue))

(def active-transactions (atom []))

(defn index-of-transaction [transactions trx]
  (->> transactions
       (map-indexed vector)
       (filter #(-> % second :trx (identical? trx)))
       ffirst))

(defn add-reference-to-transaction [transactions trx reference]
  (let [i (or (index-of-transaction transactions trx)
              (count transactions))
        m2 (get transactions i {:trx trx :references #{}})
        m3 (update m2 :references conj reference)]
    (assoc transactions i m3)))

(defn remove-reference-from-transaction [transactions trx reference]
  (if-let [i (index-of-transaction transactions trx)]
    (update-in transactions [i :references] disj reference)
    transactions))

(defn remove-transaction-from-transactions [transactions trx]
  (if-let [i (index-of-transaction transactions trx)]
    (vec (lazy-cat (subvec transactions 0 i) (subvec transactions (inc i))))
    transactions))

(defn ref
  "Convert a firemore reference to a firebase reference"
  ([path] (ref @firebase/FB path))
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

(defn promise->chan
  ([promise]
   (promise->chan
    promise
    identity))
  ([promise on-success]
   (promise->chan
    promise
    on-success
    (fn [error]
      (ex-info
       :promise->chan-failure
       {:error error}))))
  ([promise on-success on-failure]
   (let [c (async/chan)]
     (..
      promise
      (then  (fn [value]
               (some->> value on-success (async/put! c))))
      (catch (fn [error]
               (when-let [e (on-failure error)]
                 (js/console.error (pr-str e))
                 (async/put! c e))))
      (finally (fn [_]
                 (async/close! c))))
     c)))

(defn chan->promise
  ([c]
   (chan->promise
    c
    (fn [m] (-> m :success false?))))
  ([c reject?]
   (chan->promise
    c
    reject?
    (fn [c v] (async/close! c))))
  ([c reject? finally-fx]
   (js/Promise.
    (fn [resolve reject]
      (go
        (let [v (async/<! c)]
          (if (reject? v)
            (reject v)
            (resolve v))
          (finally-fx c v)))))))

(defn default-options []
  {:fb @firebase/FB})

(defn merge-default-options [options]
  (merge (default-options) options))

(defn set-db!
  ([reference value] (set-db! reference value nil))
  ([reference value options]
   (let [{:keys [fb transaction batch]} (merge-default-options options)
         {:keys [ref js-value]} (shared-db fb reference value)
         override (or transaction batch)]
     (if override
       (do (when transaction
             (swap! active-transactions remove-reference-from-transaction transaction reference))
           (.set override ref js-value))
       (promise->chan
        (.set ref js-value))))))

(defn add-db!
  ([reference value] (add-db! reference value nil))
  ([reference value options]
   (let [{:keys [fb]} (merge-default-options options)
         {:keys [ref js-value]} (shared-db fb reference value)]
     (promise->chan
      (.add ref js-value)
      (fn [docRef]
        {:id (.-id docRef)})))))

(defn update-db!
  ([reference value] (update-db! reference value nil))
  ([reference value options]
   (let [{:keys [fb transaction batch]} (merge-default-options options)
         {:keys [ref js-value]} (shared-db fb reference value)
         override (or transaction batch)]
     (if override
       (do (when transaction
             (swap! active-transactions remove-reference-from-transaction transaction reference))
           (.update override ref js-value))
       (promise->chan
        (.update ref js-value))))))

(defn delete-db!
  ([reference] (delete-db! reference nil))
  ([reference options]
   (let [{:keys [fb transaction batch]} (merge-default-options options)
         {:keys [ref]} (shared-db fb reference nil)
         override (or transaction batch)]
     (if override
       (do (when transaction
             (swap! active-transactions remove-reference-from-transaction transaction reference))
           (.delete override ref))
       (promise->chan
        (.delete ref))))))

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
   (let [{:keys [fb transaction]} (merge-default-options options)
         {:keys [ref query]} (shared-db fb reference)]
     (if query
       (promise->chan
        (.get (filter-by-query ref query))
        (fn [snapshot]
          (let [a (atom [])]
            (.forEach snapshot #(->> % doc-upgrader (swap! a conj)))
            @a)))
       (promise->chan
        (if transaction
          (do
            (swap! active-transactions add-reference-to-transaction transaction reference)
            (.get transaction ref))
          (.get ref))
        (fn [doc]
          (->> doc doc-upgrader)))))))

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

(defn noop-unwritten-reads! [transactions trx]
  (doseq [unwritten-reference (->> (index-of-transaction transactions trx)
                                   (get transactions)
                                   :references)]
    (update-db! unwritten-reference {} {:transaction trx})))

(defn transact-db!
  ([update-fx] (transact-db! update-fx nil))
  ([update-fx options]
   (let [{:keys [fb]} (merge-default-options options)
         c (async/chan)
         new-update-fx (fn [trx]
                         (-> (update-fx trx)
                             chan->promise
                             (.then    #(do (noop-unwritten-reads! @active-transactions trx)
                                            %))
                             (.finally #(swap! active-transactions remove-transaction-from-transactions trx))))]
     (-> fb
         firebase/db
         (.runTransaction new-update-fx)
         (.then    #(async/put! c %))
         (.catch   #(js/console.error %))
         (.finally #(async/close! c)))
     c)))

(defn create-batch
  ([] (create-batch @firebase/FB))
  ([fb]
   (-> fb firebase/db (.batch))))

(defn commit-batch! [btx]
  (promise->chan
   (.commit btx)))
