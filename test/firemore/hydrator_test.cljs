(ns firemore.hydrator-test
  (:require
   [cljs.core.async :as async]
   [cljs.test :as t :include-macros true]
   [firemore.hydrator :as sut]
   [firemore.firestore :as firestore]))

(defn change-watcher [atm]
  (let [c (async/chan)]
    (add-watch
     atm
     :on-change
     (fn [k r o n]
       (async/put! c n)))
    c))

(t/deftest test-hydrator-missing-document
  (let [a (atom {})
        c (change-watcher a)]
    (t/async
     done
     (async/go
       (sut/add! a [:hydrator] [:test-empty-collection "MISSING_DOC"])
       (let [m (async/<! c)]
         (t/is (= {:hydrator [:test-empty-collection "MISSING_DOC"]}
                  (:firemore m)))
         (t/is (= {:hydrator {}}
                  (:firestore m))))
       (let [m (async/<! c)]
         (t/is (= {:hydrator [:test-empty-collection "MISSING_DOC"]}
                  (:firemore m)))
         (t/is (= {:hydrator {}}
                  (:firestore m))))
       (done)))))

(t/deftest test-hydrator-empty-collection
  (let [a (atom {})
        c (change-watcher a)]
    (t/async
     done
     (async/go
       (sut/add! a [:hydrator] [:test-empty-collection])
       (let [m (async/<! c)]
         (t/is (= {:hydrator [:test-empty-collection]}
                  (:firemore m)))
         (t/is (= {:hydrator []}
                  (:firestore m))))
       (let [m (async/<! c)]
         (t/is (= {:hydrator [:test-empty-collection]}
                  (:firemore m)))
         (t/is (= {:hydrator []}
                  (:firestore m))))
       (done)))))


