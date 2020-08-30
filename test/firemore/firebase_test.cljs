(ns firemore.firebase-test
  (:require
   [firemore.firebase :as sut]
   [cljs.test :as t :include-macros true]))

(t/deftest fundamentals-test
  (t/is (some? @sut/FB))
  (t/is (some? (sut/db @sut/FB)))
  (t/is (some? (sut/auth @sut/FB))))
