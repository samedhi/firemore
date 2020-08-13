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

;; TODO: Rather than being map of paths, be a map of atoms of paths.
(def ATM->PATH->LISTEN_MAP (atom {}))

(defn nil-when-empty? [coll]
  (when-not (empty? coll)
    coll))

(defn dissoc-in-internal [m ks]
  (let [[k & ks-rest] ks]
    (nil-when-empty?
     (case (count ks)
       0 (throw (ex-info "Cannot dissoc from empty ks" m))
       1 (dissoc m k)
       (if-let [new-v (dissoc-in-internal (get m k) ks-rest)]
         (assoc m k new-v)
         (dissoc m k))))))

(defn dissoc-in [m ks]
  (or (dissoc-in-internal m ks) {}))

#_(dissoc-in {:a 1 :b {:c 2 :d {:f 3}}} [:b :d :f])

(defn subscribe-path [atm->path->listen-map atm reference path]
  (when (and (contains? atm->path->listen-map atm)
             (contains? (get atm->path->listen-map atm) path))
    (throw
     (ex-info "atm->path is already registered" {:atm atm :path path})))
  (let [listen-map (firestore/listen reference)]
    (assoc-in atm->path->listen-map [atm path] listen-map)))

(defn unsubscribe-path [atm->path->listen-map atm path]
  ;; This section shuts down the state machine feeding the value at `path` by
  ;; calling unsubscribe; this is "side effect" code.
  (-> (get-in atm->path->listen-map [atm path])
      :unsubscribe
      (apply []))
  ;; Remove the path from the path->listen-map.
  (dissoc-in atm->path->listen-map [atm path]))

(defn watch!
  ([atm reference] (watch! atm reference reference))
  ([atm reference path]
   (let [{:keys [c]} (-> (swap! ATM->PATH->LISTEN_MAP subscribe-path atm reference path)
                         (get-in [atm path]))]
     (swap! atm assoc-in path config/LOADING)
     (go-loop []
       (when-let [v (async/<! c)]
         (swap! atm assoc-in path v)
         (recur))))))

(defn unwatch! [atm path]
  (swap! ATM->PATH->LISTEN_MAP unsubscribe-path atm path)
  (swap! atm dissoc-in path))
