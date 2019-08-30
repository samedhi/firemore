(ns firemore.core-test
  (:require
   [firemore.core :as sut]
   [cljs.test :as t :include-macros true]))

(t/deftest conversion-test
  (t/are [m] (= m (-> m sut/clj->fire sut/fire->clj))
    {}
    {:string "String"}
    {:integer 1}
    {:float 1.0}
    {:boolean true}
    {:nil nil}
    {:instant (js/Date)}))

#_(t/deftest conversion-test
  (t/is (= true false)))
