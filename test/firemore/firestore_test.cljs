(ns firemore.firestore-test
  (:require
   [cljs.core.async :as async]
   [cljs.test :as t :include-macros true]
   [firemore.config :as config]
   [firemore.firestore :as sut]
   [cljs.test :as test]))

(t/deftest keywordizing-test
  (t/are [k s] (= (sut/keywordize->str k) s)
    :a ":a"
    :a/b ":a/b")
  (t/are [s k] (= (sut/str->keywordize s) k)
    ":a" :a
    ":a/b" :a/b)
  (t/are [k] (-> k sut/keywordize->str sut/str->keywordize (= k))
    :a
    :a/b))

(t/deftest conversion-test
  (t/are [m] (= m (-> m sut/jsonify sut/clojurify))
    {}
    {:a "1" :b 2 :c 3.1}
    {:a.real.long.key/is-awesome "foo"}))

(t/deftest replace-timestamp-test
  (let [m {:a config/TIMESTAMP}]
    (t/is (not= config/TIMESTAMP
                (->  sut/replace-timestamp :a)))
    (t/is (some? (-> m sut/replace-timestamp :a)))))

(t/deftest expand-query-test
  (t/are [input-query output-query] (= output-query (sut/expand-query input-query))
    {}
    {}

    {:where ["population" "<" 10000]}
    {:where [["population" "<" 10000]]}

    {:order ["population" ["state" "desc"]]}
    {:order [["population" "asc"] ["state" "desc"]]}))

(t/deftest get-and-set-test
  (let [reference ["test" "get-and-set-test"]
        m {:string "get-and-set-test"}]
    (t/async
     done
     (async/go
       (t/is (nil? (async/<! (sut/set-db! reference m))))
       (t/is (= m  (async/<! (sut/get-db reference))))
       (done)))))

(t/deftest get-and-add-test
  (let [reference ["test"]
        m {:string "get-and-add-test"}]
    (t/async
     done
     (async/go
       (let [{:keys [id]} (async/<! (sut/add-db! reference m))]
         (t/is (some? id))
         (t/is (= m (async/<! (sut/get-db (conj reference id)))))
         (done))))))

(t/deftest delete-test
  (t/async
   done
   (async/go
     (let [reference ["test" "delete-me"]
           m {:string "delete-test"}]
       (t/is (nil? (async/<! (sut/set-db! reference m))))
       (t/is (= m  (async/<! (sut/get-db reference))))
       (t/is (nil? (async/<! (sut/delete-db! reference))))
       (t/is (= {} (async/<! (sut/get-db reference))))
       (done)))))

(t/deftest update-test
  (t/async
   done
   (async/go
     (let [reference ["test" "update-test"]
           m1 {:string "update-test"}
           m2 {:integer 1}]
       (t/is (nil?            (async/<! (sut/set-db! reference m1))))
       (t/is (= m1            (async/<! (sut/get-db reference))))
       (t/is (nil?            (async/<! (sut/update-db! reference m2))))
       (t/is (= (merge m1 m2) (async/<! (sut/get-db reference))))
       (done)))))

(t/deftest listening-test
  (let [reference ["test" "listening-test"]]
    (t/async
     done
     (async/go
       (async/<! (sut/delete-db! reference))
       (let [{:keys [c unsubscribe]} (sut/listen-db reference)
             m1 {:string "listening-test-1"}
             m2 {:string "listening-test-2"}]
         (t/is (= config/NO_DOCUMENT (async/<! c)))
         (t/is (nil?                 (async/<! (sut/set-db! reference m1))))
         (t/is (= m1                 (async/<! c)))
         (t/is (nil?                 (async/<! (sut/set-db! reference m2))))
         (t/is (= m2                 (async/<! c)))
         (t/is (nil?                 (async/<! (sut/delete-db! reference))))
         (t/is (= config/NO_DOCUMENT (async/<! c)))
         (unsubscribe)
         (done))))))

(def query-fixture
  {"SF" {:name "San Francisco"
         :state "CA"
         :country "USA"
         :capital false
         :population 860000}
   "LA"  {:name "Los Angeles"
          :state "CA"
          :country "USA"
          :capital false
          :population 3900000}
   "DC"  {:name "Washington, D.C."
          :state nil
          :country "USA"
          :capital false
          :population 680000}
   "TOK" {:name "Tokyo"
          :state nil
          :country "Japan"
          :capital false
          :population 9000000000}
   "BJ"  {:name "Beijing"
          :state nil
          :country "China"
          :capital false
          :population 21500000}})

(defn write-fixture [fixture]
  (doseq [[k v] fixture]
    (sut/set-db! ["cities" k] v)))

;; The fixture data is never modified. This only needs to be written once...
#_(write-fixture query-fixture)

;; confirm fixtures are written
#_(async/go (println (async/<! (sut/get-db ["cities"]))))

(defn grab-all [c]
  (let [c2 (async/chan)]
    (async/go-loop [acc []]
      (if-let [m (async/<! c)]
        (recur (conj acc m))
        (do (async/put! c2 acc)
            (async/close! c2))))
    c2))

(t/deftest get-collection-test
  (t/async
   done
   (async/go
     (let [ms (async/<! (grab-all (sut/get-db ["cities"])))]
       (t/is (= (count ms) 5))
       (t/is (set (map :name ms) (set (map :name query-fixture))))
       (done)))))

(def test-city {:name "testacles" :population 1})

(t/deftest watch-collection-test
  (t/async
   done
   (async/go
     ;; Clear out the TEST city in case it is still there
     (async/<! (sut/delete-db! ["cities" "TEST"]))
     (let [{:keys [c unsubscribe]} (sut/listen-db ["cities"])
           cities (loop [acc []]
                    (let [new-acc (conj acc (async/<! c))]
                      (println new-acc)
                      (if (< (count new-acc) 5)
                        (recur new-acc)
                        new-acc)))]
       ;; Exhaust out all the standard cities
       (t/is (= 5 (count cities)))
       (t/is (set (map :name cities) (set (map :name query-fixture))))
       ;; Add in one additional city
       (async/<! (sut/set-db! ["cities" "TEST"] test-city))
       ;; Confirm that we see additional city
       (t/is (= test-city (async/<! c)))
       ;; Change population of TEST city
       (async/<! (sut/update-db! ["cities" "TEST"] {:population 2}))
       ;; Confirm that we see change to TEST city
       (t/is (= (assoc test-city :population 2) (async/<! c)))
       ;; Delete TEST city
       (async/<! (sut/delete-db! ["cities" "TEST"]))
       ;; Confirm deletion  (empty map means deleted).
       (t/is (= config/NO_DOCUMENT (async/<! c)))
       )
     (done)
     )
   ))


(async/go (println (async/<! (sut/delete-db! ["cities" "TEST"]))))
