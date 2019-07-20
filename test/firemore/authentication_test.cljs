(ns firemore.authentication-test
  (:require
   [firemore.authentication :as sut]
   [cljs.core.async :as async]
   [cljs.test :as t :include-macros true]
   [clojure.string :as string]))

(t/deftest anonymous-login!-test
  (t/async
   done
   (t/is (some? (sut/anonymous-login!)))
   (async/go-loop []
     (if-let [m @sut/user]
       (do
         (t/is (-> m :uid (complement string/blank?)))
         (done))
       (do
         (async/<! (async/timeout 100))
         (recur))))))
