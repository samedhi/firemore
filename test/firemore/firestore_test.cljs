(ns firemore.firestore-test
  (:require
   [cljs.core.async :as async]
   [cljs.test :as t :include-macros true]
   [firemore.config :as config]
   [firemore.firestore :as sut]))

(def cities-fixture
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
    (sut/set-db! [:cities k] v)))

;; The fixture data is never modified. This only needs to be written once...
#_(write-fixture cities-fixture)

;; confirm fixtures are written
#_(async/go (println (async/<! (sut/get-db [:cities]))))

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
  (let [reference [:test "get-and-set-test"]
        m {:string "get-and-set-test"}]
    (t/async
     done
     (async/go
       (t/is (nil? (async/<! (sut/set-db! reference m))))
       (t/is (= m  (async/<! (sut/get-db reference))))
       (done)))))

(t/deftest get-and-add-test
  (let [reference [:test]
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
     (let [reference [:test "delete-me"]
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
     (let [reference [:test "update-test"]
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
             m1                      {:string "listening-test-1"}
             m2                      {:string "listening-test-2"}]
         (t/is (= config/NO_DOCUMENT (async/<! c)))
         ;; Confirm that the document shows up at all
         (t/is (nil?                 (async/<! (sut/set-db! reference m1))))
         (t/is (= m1                 (async/<! c)))
         (t/is (= m1                 (async/<! c)))

         ;; Confirm that first is :pending? true, then server confirms and :pending? false
         (t/is (nil?                 (async/<! (sut/set-db! reference m2))))
         (t/is (true?                (-> (async/<! c) meta :pending?)))
         (t/is (false?               (-> (async/<! c) meta :pending?)))

         ;; This is a bit surprising as it does not cycle through :pending? true -> false
         ;; Does this mean that delete is always a server based thing?
         (t/is (nil? (async/<! (sut/delete-db! reference))))
         (let [m (async/<! c)]
           (t/is (= config/NO_DOCUMENT m))
           (t/is (false? (-> m meta :exist?))))
         (unsubscribe)
         (done))))))

(t/deftest get-collection-test
  (t/async
   done
   (async/go
     ;; In case it was not cleared
     (async/<! (sut/delete-db! [:cities "TEST_CITY"]))
     (let [ms (async/<! (sut/get-db [:cities]))]
       (t/is (= (count ms) 5))
       (t/is (set (map :name ms) (set (map :name cities-fixture))))
       (done)))))

(def test-city {:name "testacles" :population 1})

(t/deftest listen-db-test
  (t/async
   done
   (async/go
     ;; Clear out the TEST city in case it is still there
     (async/<! (sut/delete-db! [:cities "TEST_CITY"]))
     (let [{:keys [c unsubscribe]} (sut/listen-db [:cities])
           cities (loop [acc []]
                    (let [new-acc (conj acc (async/<! c))]
                      (if (< (count new-acc) 5)
                        (recur new-acc)
                        new-acc)))]
       ;; Exhaust out all the standard cities
       (t/is (= 5 (count cities)))
       (t/is (set (map :name cities) (set (map :name cities-fixture))))
       ;; Add in one additional TEST city
       (async/<! (sut/set-db! [:cities "TEST_CITY"] test-city))
       ;; Confirm that we see additional TEST city
       (t/is (= test-city (async/<! c)))
       (t/is (= test-city (async/<! c)))
       ;; Change population of TEST city
       (async/<! (sut/update-db! [:cities "TEST_CITY"] {:population 2}))
       ;; Confirm that we see change to TEST city
       (t/is (= (assoc test-city :population 2) (async/<! c)))
       (t/is (= (assoc test-city :population 2) (async/<! c)))
       ;; Delete TEST city
       (t/is (nil? (async/<! (sut/delete-db! [:cities "TEST_CITY"]))))
       ;; Confirm deletion of TEST
       ;; TODO: Still surprising that it is always "synchronous"
       (let [m (async/<! c)]
         (t/is (false? (-> m meta :exist?)))
         (t/is (false? (-> m meta :pending?))))
       (unsubscribe)
       (done)))))

;; TODO - Can't figure out why this test isn't stable

#_(t/deftest listen-collection-db-test
  (t/async
   done
   (async/go
     ;; Clear out the TEST city in case it is still there
     (async/<! (sut/delete-db! [:cities "TEST_CITY"]))
     (let [{:keys [c unsubscribe]} (sut/listen-collection-db [:cities])]
       ;; Get all the standard cities
       (t/is (= (->> cities-fixture vals (map :name) set)
                (->> (async/<! c) (map :name) set)))
       ;; Add in one additional TEST city
       (async/<! (sut/set-db! [:cities "TEST_CITY"] test-city))
       ;; Confirm that we see additional TEST city
       ;; TODO - What is this doing? Why is it necessary?
       (async/<! c)
       (t/is (= test-city (->> (async/<! c) (filter #(-> % :name (= "testacles"))) first)))
       ;; Delete TEST city
       (t/is (nil? (async/<! (sut/delete-db! [:cities "TEST_CITY"]))))
       ;; Confirm deletion of TEST
       (async/<! (async/timeout 100))
       (t/is (= (->> cities-fixture vals (map :name) set)
                (->> (async/<! c) (map :name) set)))
       (unsubscribe)
       (done)))))

(t/deftest all-california-test
  (t/async
   done
   (async/go
     (let [actual (async/<! (sut/get-db [:cities {:where [":state" "==" "CA"]}]))
           expected (->> cities-fixture vals (filter #(-> % :state (= "CA"))))]
       (t/is (= (set (map :name expected)) (set (map :name actual))))
       (done)))))

(t/deftest all-capital-test
  (t/async
   done
   (async/go
     (let [actual (async/<! (sut/get-db [:cities {:where [":capital" "==" true]}]))
           expected (->> cities-fixture vals (filter #(-> % :capital)))]
       (t/is (= (set (map :name expected)) (set (map :name actual))))
       (done)))))

(t/deftest smaller-city-test
  (t/async
   done
   (async/go
     (let [actual (async/<! (sut/get-db [:cities {:where [":population" "<" (* 100 1000)]}]))
           expected (->> cities-fixture vals (filter #(-> % :population (< (* 100 1000)))))]
       (t/is (= (set (map :name expected)) (set (map :name actual))))
       (done)))))


;; Following requires a "compound index" to pass... If it isn't passing go into the browser console and you
;; will see a index that you need to click. Doing so will create the index for you.
(t/deftest compound-query-test
  (t/async
   done
   (async/go
     (let [actual (async/<! (sut/get-db [:cities {:where [[":country" "==" "USA"]
                                                           [":population" "<" (* 1000 1000)]]}]))
           expected (->> cities-fixture
                         vals
                         (filter #(-> % :population (< (* 1000 1000))))
                         (filter #(-> % :country (= "USA"))))]
       (t/is (= (set (map :name expected)) (set (map :name actual))))
       (done)))))

(t/deftest order-by-state-and-population-test
  (t/async
   done
   (async/go
     (let [actual (async/<! (sut/get-db [:cities {:order [":state" [":population" "desc"]]}]))
           expected (->> cities-fixture
                         vals
                         (sort-by (fn [m] [(:state m) (-> m :population (* -1))])))]
       (t/is (= (map :name expected) (map :name actual)))
       (done)))))

(t/deftest order-by-state-and-population-and-limit-to-two-test
  (t/async
   done
   (async/go
     (let [actual (async/<! (sut/get-db [:cities {:order [":state" [":population" "desc"]]
                                                   :limit 2}]))
           expected (->> cities-fixture
                         vals
                         ;; nil is evidently less than 'a'
                         (sort-by (fn [m] [(:state m) (-> m :population (* -1))]))
                         (take 2))]
       (t/is (= (map :name expected) (map :name actual)))
       (done)))))
