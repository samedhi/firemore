(ns firemore.core-test
  (:require
   [firemore.core :as sut]
   [cljs.test :as t :include-macros true]))

(t/deftest mouse-test
  (t/testing "basic"
    (t/are [a b] (= a b)
      0 0
      true true
      false false)))
