(ns firemore.authentication-test
  (:require
   [firemore.authentication :as sut]
   [cljs.core.async :as async]
   [cljs.test :as t :include-macros true]
   [clojure.string :as string]
   [firemore.authentication :as authentication]))

(t/deftest login-anonymously!-test
  (t/async
   done
   (let [not-blank? (complement string/blank?)
         done-called (atom false)
         done-fx #(when-not @done-called
                    (reset! done-called true)
                    (done))
         watcher (fn [_ _ _ n]
                   (when n
                     (t/is (-> n :uid not-blank?))
                     (t/is (-> n :anonymous? true?))
                     (t/is (some? n))
                     (done-fx)))]
     (add-watch authentication/user-atom :watch-test watcher)
     (sut/login-anonymously!))))
