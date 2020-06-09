(ns firemore.authentication
  (:require
   [cljs.core.async :as async]
   [firemore.firebase :as firebase]
   [firemore.auth-ui :as auth-ui]
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

(defonce init-auth-ui
  (when (:enabled? config/AUTH_CONFIG)
    (let [auth (firebase/auth firebase/FB)
          auth-ui (js/firebaseui.auth.AuthUI. auth)
          {:keys [container-selector]} config/AUTH_CONFIG
          config (-> config/AUTH_CONFIG
                     (select-keys auth-ui/config-keys)
                     clj->js)]
      (.start auth-ui container-selector config)
      true)))

(defn set-style-on-auth-ui [value]
  (-> config/AUTH_CONFIG
      :container-selector
      (js/document.querySelector)
      (.. -style)
      (set! value)))

(defn show-auth-ui []
  (set-style-on-auth-ui "display: block"))

(defn hide-auth-ui []
  (set-style-on-auth-ui "display: none"))
