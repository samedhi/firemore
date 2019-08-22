 (ns firemore.firebase
  (:require
   [firemore.config :as config]))

(def OPTS
  #js {:apiKey     config/FIREBASE_API_KEY
       :authDomain config/FIREBASE_AUTH_DOMAIN
       :projectId  config/FIREBASE_PROJECT_ID})

(defonce FB (js/firebase.initializeApp OPTS))

(defn db [firebase]
  (.firestore firebase))

(defn auth [firebase]
  (.auth firebase))
