(ns firemore.hydrator-test
  (:require
   [cljs.test :as t :include-macros true]
   [firemore.hydrator :as sut]))

(t/deftest diff-test
  (t/are [old new expected] (= expected (sut/diff old new))
    {} {:a 1} {:removed {}
               :added   {:a 1}
               :changed {}}
    {:a 1} {} {:removed {:a 1}
               :added   {}
               :changed {}}
    {:a 1} {:a 2} {:removed {}
                   :added   {}
                   :changed {:a [1 2]}}))
