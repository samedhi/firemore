(ns firemore.hydrator-test
  (:require
   [cljs.core.async :as async]
   [cljs.test :as t :include-macros true]
   [firemore.authentication :as authentication]
   [firemore.config :as config]
   [firemore.hydrator :as sut]
   [firemore.firestore :as firestore]
   [firemore.firestore-test :as firestore-test]
   [cljs.test :as test]))

(defn change-watcher [atm]
  (let [c (async/chan)]
    (add-watch
     atm
     :on-change
     (fn [k r o n]
       (async/put! c n)))
    c))

(t/deftest test-hydrator-missing-document
  (t/async
   done
   (async/go
     (let [a         (atom {})
           c         (change-watcher a)
           user-id   (async/<! (authentication/uid))
           reference [:users user-id :test-hydrator-missing-documen "MISSING_DOC"]]
       (sut/add! a [:hydrator] reference)
       (t/testing "First {} is synchronously done by sut/add!"
         (let [m (async/<! c)]
           (t/is (= {:hydrator reference}
                    (:firemore m)))
           (t/is (= {:hydrator {}}
                    (:firestore m)))))
       (t/testing "Second config/NO_DOCUMENT is due to response from server"
         (let [m (async/<! c)]
           (t/is (= {:hydrator reference}
                    (:firemore m)))
           (t/is (= {:hydrator config/NO_DOCUMENT}
                    (:firestore m)))))
       (sut/subtract! a [:hydrator])
       (done)))))

(t/deftest test-hydrator-empty-collection
  (t/async
   done
   (async/go
     (let [a         (atom {})
           c         (change-watcher a)
           user-id   (async/<! (authentication/uid))
           reference [:users user-id :test-hydrator-empty-collection]]
       (sut/add! a [:hydrator] reference)
       (t/testing "First [] is synchronously done by sut/add!"
         (let [m (async/<! c)]
           (t/is (= {:hydrator reference}
                    (:firemore m)))
           (t/is (= {:hydrator []}
                    (:firestore m)))))
       (t/testing "Second [] is due to response from server"
         (let [m (async/<! c)]
           (t/is (= {:hydrator reference}
                    (:firemore m)))
           (t/is (= {:hydrator []}
                    (:firestore m)))))
       (sut/subtract! a [:hydrator])
       (done)))))

(t/deftest test-hydrator-document
  (let [a (atom {})
        c (change-watcher a)]
    (t/async
     done
     (async/go
       (t/testing
           "start watching Tokyo"
         (sut/add! a [:tokyo] [:cities "TOK"])
         (let [m (async/<! c)]
           (t/is (= {:tokyo [:cities "TOK"]}
                    (:firemore m)))
           (t/is (= {:tokyo {}}
                    (:firestore m))))
         (let [m (async/<! c)]
           (t/is (= {:tokyo [:cities "TOK"]}
                    (:firemore m)))
           (t/is (= {:tokyo (firestore-test/cities-fixture "TOK")}
                    (:firestore m)))))

       (t/testing
           "start watching DC"
           (sut/add! a [:dc] [:cities "DC"])
           (let [m (async/<! c)]
             (t/is (= {:tokyo [:cities "TOK"]
                       :dc    [:cities "DC"]}
                    (:firemore m)))
             (t/is (= {:tokyo (firestore-test/cities-fixture "TOK")
                       :dc    {}}
                    (:firestore m))))
           (let [m (async/<! c)]
             (t/is (= {:tokyo [:cities "TOK"]
                       :dc    [:cities "DC"]}
                      (:firemore m)))
             (t/is (= {:tokyo (firestore-test/cities-fixture "TOK")
                       :dc    (firestore-test/cities-fixture "DC")}
                      (:firestore m)))))

       (t/testing
           "Stop watching Tokyo"
         (sut/subtract! a [:tokyo])
         (let [m (async/<! c)]
           (t/is (= {:dc [:cities "DC"]}
                  (:firemore m)))
           (t/is (= {:dc (firestore-test/cities-fixture "DC")}
                  (:firestore m)))))

       (t/testing
        "Stop watching DC"
        (sut/subtract! a [:dc])
        (let [m (async/<! c)]
          (t/is (= {}
                  (:firemore m)))
          (t/is (= {}
                  (:firestore m)))))
       (done)))))

#_(t/deftest test-hydrator-collection
  (let [a (atom {})
        c (change-watcher a)]
    (t/async
     done
     (async/go
       (sut/add! a [:cities] [:cities])
       (let [m (async/<! c)]
         (t/is (= {:cities [:cities]}
                  (:firemore m)))
         (t/is (= {:cities []}
                  (:firestore m))))
       (let [m (async/<! c)]
         (t/is (= {:cities [:cities]}
                  (:firemore m)))
         (t/is (= (count firestore-test/cities-fixture)
                  (-> m :firestore :cities count))))
       (sut/subtract! a [:cities])
       (let [m (async/<! c)]
         (t/is (= {}
                  (:firemore m)))
         (t/is (= {}
                  (:firestore m))))
       (done)))))
