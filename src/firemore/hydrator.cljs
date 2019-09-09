(ns firemore.hydrator
  (:require
   [cljs.core.async :as async]
   [clojure.data :as data]
   [clojure.set :as set]
   [firemore.config :as config]
   [firemore.firestore :as firestore])
  (:require-macros
   [cljs.core.async.macros :refer [go-loop go]]))

(enable-console-print!)

(defn prepend [x y]
  (apply conj x y))

;; Note: It is with a heavy heart that I add this mutable concept into this function.
;; But the alternative is that I either close over the input and output path to
;; create all the needed functions OR pass them as options to all needed functions.
;; Both solutions seem like overkill as I doubt that you are going to have more
;; than one (input, output) tuple per cljs application. Revisit this if it turns out
;; your assumptions are incorrect.
(defn prepend-input-path [path]
  (prepend @config/input-path path))

(defn prepend-output-path [path]
  (prepend @config/output-path path))

(defn handle-removed [m path]
  (when-let [reference (get-in m (prepend-input-path path))]
    (println "Destroying observer for" path "->" reference)
    (-> reference meta :unsubscribe (apply []))))

(defn subtract [m path]
  (let [input-path (prepend-input-path path)
        output-path (prepend-output-path path)]
    (handle-removed m path)
    (-> m
        (update-in (pop input-path)  dissoc (peek input-path))
        (update-in (pop output-path) dissoc (peek output-path)))))


(defn query? [reference]
  (or (-> reference peek map?)
      (-> reference count odd?)))

;; Note: Ugh. Passing a atm to a function that is going to be called in swap!....
;; The issue is that I need to have the atom so I can build the state machine. I
;; also considered putting the atom in a globally reachable place (config) or
;; having state machine be a side effect in a watch function attached to the
;; atom. Not good. Bad code. What better?
(defn handle-added [m atm path reference]
  (let [listen (if (query? reference)
                 firestore/listen-collection-db
                 firestore/listen-db)
        {:keys [c unsubscribe] :as m2} (listen reference)
        output-path (prepend-output-path path)]
    (go-loop []
      (when-let [v (async/<! c)]
        (swap! atm assoc-in output-path v)
        (recur)))
    (with-meta reference m2)))

(defn add [m atm path reference]
  (let [input-path (prepend-input-path path)
        output-path (prepend-output-path path)]
    (-> m
        (subtract path)
        (assoc-in input-path (handle-added m atm path reference))
        (assoc-in output-path (if (query? reference) [] {})))))

(defn subtract! [atm path]
  (swap! atm subtract path))

(defn add! [atm path reference]
  (swap! atm add atm path reference))
