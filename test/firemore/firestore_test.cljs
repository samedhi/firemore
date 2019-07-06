(ns firemore.firestore-test
  (:require
   [firemore.firestore :as sut]
   [cljs.test :as t :include-macros true]))

(t/deftest fundamentals-test
  (t/is (some? sut/OPTS))
  (t/is (some? sut/FB))
  (t/is (some? (sut/db sut/FB))))

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


