(ns firemore.authentication
  (:require
   [cljs.core.async :as async]
   [firemore.firebase :as firebase]
   [firemore.config :as config])
  (:require-macros
   [cljs.core.async.macros :refer [go-loop go]]))

(enable-console-print!)

(def FB firebase/FB)

(def user-atom (atom config/NO_USER))

(def user-chan (async/chan (async/sliding-buffer 1)))

(add-watch user-atom
           :value-change
           (fn [_ _ _ new]
             (async/put! user-chan new)))

(defn user-change-handler [js-user]
  (reset!
   user-atom
   (if js-user
     {:anonymous? (.-isAnonymous js-user)
      :uid        (.-uid js-user)}
     config/NO_USER)))

(-> FB firebase/auth (.onAuthStateChanged user-change-handler))

(defn login-anonymously!
  ([] (login-anonymously! FB))
  ([fb] (.signInAnonymously (firebase/auth fb))))
