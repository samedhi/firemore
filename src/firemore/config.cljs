(ns firemore.config)

(def FIREBASE_API_KEY "AIzaSyAEEGdlXMkrxbF-OWbsDffCSKMogeiRvfA")

(def FIREBASE_PROJECT_ID "inferno-8d188")

(defn https [& ds]
  (apply str "https://" ds))

(def FIREBASE_DATABASE_URL (https FIREBASE_PROJECT_ID ".firebaseio.com"))

(def FIREBASE_STORAGE_BUCKET (https FIREBASE_PROJECT_ID ".appspot.com"))

(def FIREBASE_AUTH_DOMAIN (https FIREBASE_PROJECT_ID ".firebaseapp.com"))

(def TEST_EMAIL_ADDRESS1 "test1.email@test.com")

(def TEST_EMAIL_ADDRESS2 "test2.email@test.com")

(def TEST_EMAIL_PASSWORD "Ehgcm455dyN5bpH")

(def app-max-width 1024)

(def TIMESTAMP :firemore/timestamp)

(def NO_DOCUMENT {})

(def NO_USER :firemore/no-user)

(def input-path (atom [:firemore]))

(def output-path (atom [:firestore]))
