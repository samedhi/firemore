(ns firemore.authentication-test
  (:require
   [firemore.authentication :as sut]
   [cljs.core.async :as async]
   [cljs.test :as t :include-macros true]
   [clojure.string :as string]))

(t/deftest login-anonymously!-test
  (t/async
   done
   (let [not-blank? (complement string/blank?)]
     (async/go
       (println "go started")
       (t/is (some? (sut/login-anonymously!)))
       (let [m (async/<! sut/user-chan)]
         (println "1")
         (t/is (-> m :uid not-blank?))
         (t/is (= m @sut/user-atom))
         (println "done next")
         (done)
         (println "done called"))))))
