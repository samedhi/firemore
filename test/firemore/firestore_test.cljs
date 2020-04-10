(ns firemore.firestore-test
  (:require
   [clojure.set :as set]
   [cljs.core.async :as async]
   [cljs.test :as t :include-macros true]
   [firemore.authentication :as authentication]
   [firemore.config :as config]
   [firemore.firestore :as sut]))

(def cities-fixture
  {"SF" {:name "San Francisco"
         :state "CA"
         :country "USA"
         :capital false
         :population (* 860 1000)
         :regions ["west_coast" "norcal"]}
   "LA"  {:name "Los Angeles"
          :state "CA"
          :country "USA"
          :capital false
          :population (* 3900 1000)
          :regions ["west_coast" "socal"]}
   "DC"  {:name "Washington, D.C."
          :state nil
          :country "USA"
          :capital false
          :population (* 680 1000)
          :regions ["east_coast"]}
   "TOK" {:name "Tokyo"
          :state nil
          :country "Japan"
          :capital false
          :population (* 9 1000 1000)
          :regions ["kantu" "honshu"]}
   "BJ"  {:name "Beijing"
          :state nil
          :country "China"
          :capital false
          :population (* 215000 1000)
          :regions ["jingjinji" "hebei"]}})

;; (defn write-fixture [fixture]
;;   (doseq [[k v] fixture]
;;     (sut/set-db! [:cities k] v)))

;; ;; The fixture data is never modified. This only needs to be written once...
;; (t/deftest write-fixture-data
;;   (t/async
;;    done
;;    (async/go
;;      (write-fixture cities-fixture)
;;      (async/<! (async/timeout 5000))
;;      (t/is true)
;;      (done))))

;; ;; confirm fixtures are written
;; #_(async/go (println (async/<! (sut/get-db [:cities]))))

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
  (t/async
   done
   (async/go
     (let [user-id (async/<! (authentication/uid))
           reference [:users user-id :test "get-and-set-test"]
           m {:string "get-and-set-test-value"}]
       (t/is (nil? (async/<! (sut/set-db! reference m))))
       (t/is (= m  (async/<! (sut/get-db reference))))
       (done)))))

(t/deftest get-and-add-test
  (t/async
   done
   (async/go
     (let [user-id (async/<! (authentication/uid))
           reference [:users user-id :get-and-add-test]
           m {:string "get-and-add-test-value"}
           {:keys [id]} (async/<! (sut/add-db! reference m))]
       (t/is (some? id))
       (t/is (= m (async/<! (sut/get-db (conj reference id)))))
       (done)))))

(t/deftest delete-test
  (t/async
   done
   (async/go
     (let [user-id (async/<! (authentication/uid))
           reference [:users user-id :test "delete-me"]
           m {:string "delete-test"}]
       (t/is (nil? (async/<! (sut/set-db! reference m))))
       (t/is (= m  (async/<! (sut/get-db reference))))
       (t/is (nil? (async/<! (sut/delete-db! reference))))
       (t/is (= config/NO_DOCUMENT (async/<! (sut/get-db reference))))
       (done)))))

(t/deftest update-test
  (t/async
   done
   (async/go
     (let [user-id (async/<! (authentication/uid))
           reference [:users user-id :test "update-test"]
           m1 {:string "update-test"}
           m2 {:integer 1}]
       (t/is (nil?            (async/<! (sut/set-db! reference m1))))
       (t/is (= m1            (async/<! (sut/get-db reference))))
       (t/is (nil?            (async/<! (sut/update-db! reference m2))))
       (t/is (= (merge m1 m2) (async/<! (sut/get-db reference))))
       (done)))))

(defn set-midochlorian-count-fx [trx reference]
  (async/go
    (let [{anakin-midichlorians :midichlorian} (async/<! (sut/get-db [:characters "anakin"] {:transaction trx})) ;; 27700
          {padme-midichlorians  :midichlorian} (async/<! (sut/get-db [:characters "padme"] {:transaction trx})) ;; 4700
          midichlorians-average (/ (+ padme-midichlorians anakin-midichlorians) 2)] ;; 16200
      (sut/set-db! reference {:midichlorian midichlorians-average} {:transaction trx})
      (str "midichlorians count is " midichlorians-average))))

(t/deftest transaction-test
  (t/async
   done
   (async/go
     (let [user-id (async/<! (authentication/uid))
           uuid (str (random-uuid))
           reference [:users user-id :test uuid]
           trx-fx #(set-midochlorian-count-fx % reference)]
       (t/is (= "midichlorians count is 16200" (async/<! (sut/transact-db! trx-fx))))
       (t/is (= 16200 (:midichlorian (async/<! (sut/get-db reference)))))
       (done)))))

(t/deftest listening-test
  (t/async
   done
   (async/go
     (let [user-id (async/<! (authentication/uid))
           reference [:users user-id :test "listening-test"]
           {:keys [c unsubscribe]} (sut/listen reference)
           m1                      {:string "listening-test-1"}
           m2                      {:string "listening-test-2"}]
       (t/testing "Initially there should be 'no document' at reference"
         (sut/delete-db! reference)
         (t/is (= config/NO_DOCUMENT (async/<! c))))

       (t/testing "Document should be m1 after write"
         (async/<! (sut/set-db! reference m1))
         (t/is (= m1 (async/<! c))))

       (t/testing "Document should be m2 after update"
         (async/<! (sut/set-db! reference m2))
         (t/is (= m2 (async/<! c))))

       (t/testing "Back to 'no document' after delete"
         (nil? (async/<! (sut/delete-db! reference)))
         (t/is (= config/NO_DOCUMENT (async/<! c))))

       (unsubscribe)
       (done)))))

(t/deftest get-collection-test
  (t/async
   done
   (async/go
     (let [user-id (async/<! (authentication/uid))
           ms (async/<! (sut/get-db [:cities]))]
       (t/is (= 5 (count ms)))
       (t/is (= (-> cities-fixture keys set) (->> ms (map meta) (map :id) set)))
       (done)))))

(t/deftest listen-to-document-test
  (t/async
   done
   (async/go
     (let [sf (async/<! (sut/get-db [:cities "SF"]))
           user-id (async/<! (authentication/uid))
           sf-ref [:users user-id :listen-to-document-test "SF"]
           {:keys [c unsubscribe]} (sut/listen sf-ref)]
       (t/testing "Before there was San Francisco, there was nothing."
         (t/is (= config/NO_DOCUMENT (async/<! c))))
       (t/testing "Copy San Francisco so that we can edit it."
         (async/<! (sut/set-db! sf-ref sf))
         (t/is (= sf (async/<! c))))
       (t/testing "Modify San Francisco (Godzilla attacks)."
         (async/<! (sut/update-db! sf-ref {:monsters ["Godzilla"]}))
         (t/is (= ["Godzilla"] (-> c async/<! :monsters))))
       (t/testing "Modify San Francisco (Mothra attacks)"
         (async/<! (sut/update-db! sf-ref {:monsters ["Godzilla" "Mothra"]}))
         (t/is (= ["Godzilla" "Mothra"] (-> c async/<! :monsters))))
       (t/testing "Monsters destroyed the city"
         (async/<! (sut/delete-db! sf-ref {:monsters ["Godzilla" "Mothra"]}))
         (t/is (= config/NO_DOCUMENT (async/<! c))))
       (unsubscribe)
       (done)))))

(t/deftest listen-to-collection-test
  (t/async
   done
   (async/go
     (let [[a b :as cities] (async/<! (sut/get-db [:cities]))
           user-id (async/<! (authentication/uid))
           uuid (keyword (str (random-uuid)))
           cities-ref [:users user-id uuid]
           {:keys [c unsubscribe]} (sut/listen cities-ref)]
       (t/testing "Initially our cities collection is empty."
         (t/is (empty? (async/<! c))))
       (t/testing "Add one city into our collection"
         (async/<!
          (sut/set-db! (->> a meta :id (conj cities-ref))
                       a))
         (t/is (= 1 (-> c async/<! count))))
       (t/testing "Add a second city into our collection"
         (async/<!
          (sut/set-db! (->> b meta :id (conj cities-ref))
                       b))
         (t/is (= 2 (-> c async/<! count))))
       (unsubscribe)
       (done)))))

(t/deftest listen-to-metadata-changes
  (t/async
   done
   (async/go
     (let [sf (async/<! (sut/get-db [:cities "SF"]))
           user-id (async/<! (authentication/uid))
           uuid (str (random-uuid))
           sf-ref [:users user-id :listen-to-metadata-changes uuid]
           {:keys [c unsubscribe]} (sut/listen sf-ref {:include-metadata-changes true})]
       (t/testing "Before there was San Francisco, there was nothing."
         (t/is (= config/NO_DOCUMENT (async/<! c))))
       (t/testing "Write San Francisco to the path we are watching"
         (async/<! (sut/set-db! sf-ref sf)))
       (t/testing "The first listener event is the local event"
         (let [sf-unpersisted (async/<! c)]
           (t/is (= sf sf-unpersisted))
           (t/is (true? (-> sf-unpersisted meta :pending?)))))
       (t/testing "The second listener event is the server event"
         (let [sf-persisted (async/<! c)]
           (t/is (= sf sf-persisted))
           (t/is (false? (-> sf-persisted meta :pending?)))))
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

(t/deftest array-contains-test
  (t/async
   done
   (async/go
     (let [expected (->> cities-fixture
                         vals
                         (filter #(-> % :regions set (set/intersection #{"west_coast"}) empty? not)))
           actual (async/<! (sut/get-db [:cities {:where [":regions" "array-contains" "west_coast"]}]))]
       (t/is (= (set expected) (set actual)))
       (done)))))

(t/deftest in-test
  (t/async
   done
   (async/go
     (let [expected (->> cities-fixture
                         vals
                         (filter #(->> % :country (contains? #{"USA" "Japan"}))))
           actual (async/<! (sut/get-db [:cities {:where [":country" "in" ["USA" "Japan"]]}]))]
       (t/is (= (set expected) (set actual)))
       (done)))))

(t/deftest array-contains-any-test
  (t/async
   done
   (async/go
     (let [expected
           (->> cities-fixture
                vals
                (filter #(-> %
                             :regions
                             set
                             (set/intersection #{"west_coast" "east_coast"})
                             empty?
                             not)))
           actual
           (async/<! (sut/get-db [:cities {:where [":regions"
                                                   "array-contains-any"
                                                   ["west_coast" "east_coast"]]}]))]
       (t/is (= (set expected) (set actual)))
       (done)))))

;; Following requires a "compound index" to pass... If it isn't passing go into the
;; browser console and you will see a index that you need to click. Doing so will create the
;; index for you.
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
                         (sort-by (fn [m] [(:state m) (-> m :population (* -1))]))
                         (take 2))]
       (t/is (= (map :name expected) (map :name actual)))
       (done)))))
