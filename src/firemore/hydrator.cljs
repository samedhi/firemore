(ns firemore.hydrator
  (:require
   [clojure.data :as data]
   [clojure.set :as set]))

(defn diff [old new]
  (let [[only-a only-b _] (data/diff old new)
        changed (reduce
                 (fn [m k]
                   (assoc m k [(get only-a k) (get only-b k)]))
                 {}
                 (set/intersection (set (keys only-a)) (set (keys only-b))))]
    {:removed (reduce dissoc (or only-a {}) (keys changed))
     :added   (reduce dissoc (or only-b {}) (keys changed))
     :changed changed}))

