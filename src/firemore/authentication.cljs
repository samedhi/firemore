(ns firemore.authentication
  (:require
   [cljs.core.async :as async]
   [firemore.firebase :as firebase]
   [firemore.config :as config])
  (:require-macros
   [cljs.core.async.macros :refer [go-loop go]]))

(enable-console-print!)

(def FB firebase/FB)

(def signing-in? (atom false))

(def user-atom (atom nil))

(defn user-change-handler [js-user]
  (when js-user
    (reset!
     user-atom
     {:anonymous? (.-isAnonymous js-user)
      :uid        (.-uid js-user)})))

(-> FB firebase/auth (.onAuthStateChanged user-change-handler))

(defn login-anonymously!
  ([] (login-anonymously! FB))
  ([fb]
   (when-not @signing-in?
     (.signInAnonymously (firebase/auth fb))
     (reset! signing-in? true))))

(defn logout!
  ([] (logout! FB))
  ([fb] (.signOut (firebase/auth fb))))

(defn uid []
  (let [c (async/chan)]
    (go-loop []
      (if-let [uid (:uid @user-atom)]
        (async/put! c uid)
        (do
          (login-anonymously!)
          (async/<! (async/timeout 1000))
          (recur))))
    c))

;; This is here so that you can still do live-reloading in this namespace
;; without constantly getting an error about "An AuthUI instance already exist ...";
;; of course this means you have to reload manually if you make changes here.
(defonce loaded-auth-ui-already? (atom false))

(def init-auth-ui
  (when (and (:enabled? config/auth-ui)
             (false? @loaded-auth-ui-already?))
    (let [auth (firebase/auth firebase/FB)
          auth-ui (js/firebaseui.auth.AuthUI. auth)
          {:keys [container-selector]} config/auth-ui
          config (-> config/auth-ui
                     (select-keys config/auth-ui-config-keys)
                     clj->js)]
      (.start auth-ui container-selector config)
      (reset! loaded-auth-ui-already? true))))
