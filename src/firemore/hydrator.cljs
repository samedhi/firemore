(ns firemore.hydrator
  (:require
   [cljs.core.async :as async]
   [clojure.data :as data]
   [clojure.set :as set]
   [firemore.firestore :as firestore]))

(enable-console-print!)

(defn diff-old [old new]
  (let [[only-a only-b _] (data/diff old new)
        changed (reduce
                 (fn [m k]
                   (assoc m k [(get only-a k) (get only-b k)]))
                 {}
                 (set/intersection (set (keys only-a)) (set (keys only-b))))]
    {:removed (reduce dissoc (or only-a {}) (keys changed))
     :added   (reduce dissoc (or only-b {}) (keys changed))
     :changed changed}))

(defn diff [old new]
  (let [[only-a only-b _] (data/diff old new)]
    {:removed (or only-a {})
     :added (or only-b {})}))

(defn handle-removed [{:keys [removed] :as m}]
  (doseq [[k v] removed]
    (println "Destroying observer for" k "->" v )
    (-> v meta :unsubscribe (apply []))))

(defn handle-added [added]
  (reduce-kv
   (fn [m k v]
     (println "Creating observer for" k "->" v )
     (let [{:keys [c unsubscribe] :as m2} (firestore/listen-db v)]
       (async/go
         (when-let [document (async/<! )]
           ))
       (assoc m k (with-meta v (select-keys m2 [:unsubscribe])))))
   {}
   added))
