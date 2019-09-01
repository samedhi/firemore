(ns firemore.authentication-test
  (:require
   [firemore.authentication :as sut]
   [cljs.core.async :as async]
   [cljs.test :as t :include-macros true]
   [clojure.string :as string]
   [firemore.authentication :as authentication]
   [firemore.config :as config]))

(t/deftest login-anonymously!-test
  (t/async
   done
   (let [not-blank? (complement string/blank?)
         watcher (fn [_ _ _ n]
                   (when n
                     (t/is (-> n :uid not-blank?))
                     (t/is (-> n :anonymous? true?))
                     (t/is (some? n))
                     (done)))]
     (t/is (nil? config/user-atom))
     (add-watch authentication/user-atom :watch-test watcher)
     (sut/login-anonymously!))))
