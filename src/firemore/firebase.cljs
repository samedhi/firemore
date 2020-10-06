 (ns firemore.firebase
   (:require
    [firebase.app]
    [firebase.auth]
    [firebase.firestore]
    [firemore.config :as config]))

(def FB (atom nil))

(defn https [& ds]
  (apply str "https://" ds))

(defn database-url [firebase-project-id]
  (https firebase-project-id ".firebaseio.com"))

(defn storage-bucket [firebase-project-id]
  (str firebase-project-id ".appspot.com"))

(defn auth-domain [firebase-project-id] 
  (str firebase-project-id ".firebaseapp.com"))

(def overrides (atom nil))

(defn opts->js-opts [config]
  (let [{:keys [api-key project-id]} config
        extra-overrides (dissoc config :api-key :project-id)]
    (when-not (empty? extra-overrides)
      (reset! overrides extra-overrides))
    {:apiKey api-key
     :authDomain (auth-domain project-id)
     :projectId project-id}))

(defn initialize
  ([]
   (initialize config/default-firebase-config))
  ([opts]
   (let [js-opts (-> opts opts->js-opts clj->js)
         app (js/firebase.initializeApp js-opts)]
     (reset! FB app))))

(defn db [firebase]
  (let [db (.firestore firebase)]
    (when-let [overrides @overrides]
      (.settings db (clj->js overrides)))
    db))

(defn auth [firebase]
  (.auth firebase))
