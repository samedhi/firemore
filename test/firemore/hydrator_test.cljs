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

(defn add-change-watcher [atm]
  (let [c (async/chan)]
    (add-watch
     atm
     :on-change
     (fn [k r o n]
       (async/put! c n)))
    c))

(t/deftest test-missing-document
  (t/async
   done
   (async/go
     (let [a         (atom {})
           c         (add-change-watcher a)
           user-id   (async/<! (authentication/uid))
           reference [:users user-id :test-hydrator-missing-document "MISSING_DOC"]
           path [:missing-document-test]]
       (sut/watch! a reference path)
       (t/testing "config/LOADING set as the initial value"
         (let [{:keys [missing-document-test]} (async/<! c)]
           (t/is (= missing-document-test config/LOADING))))
       (t/testing "config/NO_DOCUMENT set as the final value"
         (let [{:keys [missing-document-test]} (async/<! c)]
           (t/is (= missing-document-test config/NO_DOCUMENT))))
       (sut/unwatch! a path)
       (done)))))

(t/deftest test-empty-collection
  (t/async
   done
   (async/go
     (let [a         (atom {})
           c         (add-change-watcher a)
           user-id   (async/<! (authentication/uid))
           reference [:users user-id :test-hydrator-empty-collection {}]
           path      [:empty-collection-test]]
       (sut/watch! a reference path)
       (t/testing "config/LOADING set as the initial value"
         (let [{:keys [empty-collection-test]} (async/<! c)]
           (t/is (= empty-collection-test config/LOADING))))
       (t/testing "[] set as the final value"
         (let [{:keys [empty-collection-test]} (async/<! c)]
           (t/is (= empty-collection-test []))))
       (sut/unwatch! a path)
       (done)))))

(t/deftest test-watching-documents
  (let [a (atom {})
        c (add-change-watcher a)]
    (t/async
     done
     (async/go
       (t/testing "start watching Tokyo"
         (sut/watch! a [:cities "TOK"] [:tokyo])
         (t/is (= config/LOADING
                  (-> c async/<! :tokyo)))
         (t/is (= (firestore-test/cities-fixture "TOK")
                  (-> c async/<! :tokyo))))

       (t/testing "start watching DC"
         (sut/watch! a [:cities "DC"] [:dc])
         (t/is (= config/LOADING
                  (-> c async/<! :dc)))
         (t/is (= (firestore-test/cities-fixture "DC")
                  (-> c async/<! :dc))))

       (t/testing "Stop watching Tokyo"
         (sut/unwatch! a [:tokyo])
         (t/is (= nil
                  (-> c async/<! :tokyo))))

       (t/testing "Stop watching DC"
         (sut/unwatch! a [:dc])
         (t/is (= nil
                  (-> c async/<! :dc))))

       (done)))))

(t/deftest test-watching-collection
  (let [a (atom {})
        c (add-change-watcher a)]
    (t/async
     done
     (async/go
       (t/testing "Start watching cities"
         (sut/watch! a [:cities {}] [:cities])
         (t/is (= config/LOADING
                  (-> c async/<! :cities)))
         (t/is (= (->> firestore-test/cities-fixture
                         vals
                         (map :name)
                         set)
                    (->> c
                         async/<!
                         :cities
                         (map :name)
                         set))))
       (t/testing "Stop watching cities"
         (sut/unwatch! a [:cities])
         (t/is (= nil
                  (-> c async/<! :dc))))
       (done)))))
