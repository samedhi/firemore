(ns firemore.config
  (:require
   [clojure.string :as string]
   [firemore.auth-ui :as auth-ui]
   [goog.object :as goog.object]))

(def TIMESTAMP :firemore/timestamp)

(def NO_DOCUMENT :firemore/no-document)

(def LOADING :firemore/loading)

(def required-config
  ["FIREBASE_API_KEY"
   "FIREBASE_PROJECT_ID"])

(def default-firebase-config
  {"FIREBASE_API_KEY"    "AIzaSyAEEGdlXMkrxbF-OWbsDffCSKMogeiRvfA"
   "FIREBASE_PROJECT_ID" "inferno-8d188"
   "FIREBASE_AUTH_UI_CONFIG" nil})

(def provided-firebase-config
  (reduce-kv
   (fn [m k _]
     (assoc m k (goog.object/get js/window k)))
   {}
   default-firebase-config))

(def use-default-values (atom false))

(let [missing-required-keys (->> (select-keys provided-firebase-config required-config)
                                 (filter (fn [[_ v]] (string/blank? v)))
                                 (map first))]
  (when-not (empty? missing-required-keys)
    (reset! use-default-values true)
    (js/console.warn
     (str "You are connecting to the DEFAULT Firebase instance "
          "[" (default-firebase-config "FIREBASE_PROJECT_ID") "].\n\n"
          (string/join
           "\n- "
           (cons "Please set the following value(s) upon js/window to connect to YOUR Firebase instance:"
                 missing-required-keys))))))

(defn grab-value [k]
  (if @use-default-values
    (default-firebase-config k)
    (provided-firebase-config k)))

(def FIREBASE_API_KEY (grab-value "FIREBASE_API_KEY"))

(def FIREBASE_PROJECT_ID (grab-value "FIREBASE_PROJECT_ID"))

(def AUTH_CONFIG
  (let [js-obj (get provided-firebase-config "FIREBASE_AUTH_UI_CONFIG")
        auth-ui-config (js->clj js-obj :keywordize-keys true)]
    (merge
     auth-ui/default-config
     auth-ui-config)))

;; The following values are derived from FIREBASE_API_KEY and FIREBASE_PROJECT_ID for firebase.

(defn https [& ds]
  (apply str "https://" ds))

(def FIREBASE_DATABASE_URL (https FIREBASE_PROJECT_ID ".firebaseio.com"))

(def FIREBASE_STORAGE_BUCKET (str FIREBASE_PROJECT_ID ".appspot.com"))

(def FIREBASE_AUTH_DOMAIN (str FIREBASE_PROJECT_ID ".firebaseapp.com"))
