(ns firemore.firestore-test
  (:require
   [firemore.firestore :as sut]
   [cljs.test :as t :include-macros true]))

(t/deftest fundamentals-test
  (t/is (some? sut/OPTS))
  (t/is (some? sut/FB))
  (t/is (some? (sut/db sut/FB))))

(t/deftest keywordizing-test
  (t/testing "keyword->str"
    (t/are [k s] (= (sut/keyword->str k) s)
      :a ":a"
      :a/b ":a/b"))
  (t/testing "str->keyword"
    (t/are [s k] (= (sut/str->keyword s) k)
      ":a" :a
      ":a/b" :a/b))
  (t/testing "full cycle"
    (t/are [k] (-> k sut/keyword->str sut/str->keyword (= k))
      :a
      :a/b)))
