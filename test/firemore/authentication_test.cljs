(ns firemore.authentication-test
  (:require
   [firemore.authentication :as sut]
   [cljs.core.async :as async]
   [cljs.test :as t :include-macros true]
   [clojure.string :as string]))

(t/deftest login-anonymously!-test
  (t/async
   done
   (async/go
     (t/is (some? (sut/login-anonymously!)))
     (let [m (async/<! sut/user-chan)]
       (t/is (-> m :uid (complement string/blank?)))
       (t/is (= m @sut/user-atom))
       (done)))))
