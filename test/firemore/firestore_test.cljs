(ns firemore.firestore-test
  (:require
   [firemore.firestore :as sut]
   [cljs.test :as t :include-macros true]))

(t/deftest fundamentals-test
  (t/is (some? sut/OPTS))
  (t/is (some? sut/FB)))
