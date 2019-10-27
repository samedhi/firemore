(ns firemore.config)

(defn https [& ds]
  (apply str "https://" ds))

(def FIREBASE_API_KEY "AIzaSyAEEGdlXMkrxbF-OWbsDffCSKMogeiRvfA")

(def FIREBASE_PROJECT_ID "inferno-8d188")

(def FIREBASE_DATABASE_URL (https FIREBASE_PROJECT_ID ".firebaseio.com"))

(def FIREBASE_STORAGE_BUCKET (https FIREBASE_PROJECT_ID ".appspot.com"))

(def FIREBASE_AUTH_DOMAIN (https FIREBASE_PROJECT_ID ".firebaseapp.com"))

(def TIMESTAMP :firemore/timestamp)

(def NO_DOCUMENT {})
