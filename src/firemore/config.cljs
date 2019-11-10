(ns firemore.config
  (:require
   [clojure.string :as string]
   [goog.object :as goog.object]))

(def default-firebase-config
  {"FIREBASE_API_KEY"    "AIzaSyAEEGdlXMkrxbF-OWbsDffCSKMogeiRvfA"
   "FIREBASE_PROJECT_ID" "inferno-8d188"})

(def provided-firebase-config
  (reduce-kv
   (fn [m k _]
     (assoc m k (goog.object/get js/window k)))
   {}
   default-firebase-config))

(def use-default-values (atom false))

(let [unset-values (filter (fn [[k v]] (string/blank? v)) provided-firebase-config)]
  (when-not (empty? unset-values)
    (reset! use-default-values true)
    (js/console.warn
     (str "You are connecting to the DEFAULT Firebase instance "
          "[" (default-firebase-config "FIREBASE_PROJECT_ID") "].\n\n"
          (string/join
           "\n- "
           (cons "Please set the following value(s) upon js/window to connect to YOUR Firebase instance:"
                 (map first unset-values)))))))

(defn grab-value [k]
  (or (and @use-default-values
           (default-firebase-config k))
      (provided-firebase-config k)
      (throw (ex-info "Unexpected key" {:key k}))))

(def FIREBASE_API_KEY (grab-value "FIREBASE_API_KEY"))

(def FIREBASE_PROJECT_ID (grab-value "FIREBASE_PROJECT_ID"))

;; The following values are derived from FIREBASE_API_KEY and FIREBASE_PROJECT_ID

(defn https [& ds]
  (apply str "https://" ds))

(def FIREBASE_DATABASE_URL (https FIREBASE_PROJECT_ID ".firebaseio.com"))

(def FIREBASE_STORAGE_BUCKET (https FIREBASE_PROJECT_ID ".appspot.com"))

(def FIREBASE_AUTH_DOMAIN (https FIREBASE_PROJECT_ID ".firebaseapp.com"))

;; Constants

(def TIMESTAMP :firemore/timestamp)

(def NO_DOCUMENT :firemore/no-document)
