(ns firemore.authentication
  (:require
   [firemore.firebase :as firebase]))

(enable-console-print!)

(def FB firebase/FB)

(def user (atom nil))

(defn user-change-handler [js-user]
  (reset! user
          (when js-user
            {:anonymous? (.-isAnonymous js-user)
             :uid        (.-uid js-user)})))

(add-watch user
           :user-change
           (fn [_ _ _ n]
             (if n
               (println "User is signed in as" (:uid n) ".")
               (println "User is signed out."))))

(-> FB firebase/auth (.onAuthStateChanged user-change-handler))

(defn anonymous-login!
  ([] (anonymous-login! FB))
  ([fb] (.signInAnonymously (firebase/auth fb))))
