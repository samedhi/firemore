(ns firemore.auth-ui)

(def default-config
  {;; Do you want to enable the UI authentication?
   :enabled false

   ;; URL that you will redirect to upon signin success
   :signInSuccessUrl "/"

   ;; The options you want enabled as part of this firebase app.
   ;; Why isn't this queried from firebase itself? Why do I have to manually enable it?
   ;; List comes from:
   ;; https://github.com/firebase/firebase-js-sdk/blob/a98a76648f0683e86d6b1f0ee89b3d5548c30677/packages/auth/src/idp.js#L37-L47
   :signInOptions [
                   "anonymous"    ;; Continue as guest
                   "facebook.com" ;; Sign in with Facebook
                   "github.com"   ;; Sign in with GitHub
                   "google.com"   ;; Sign in with Google
                   "twitter.com"  ;; Sign in with Twitter
                   "password"     ;; Sign in with email
                   "phone"        ;; Sign in with phone
                   ]

   ;; Your Terms of Service
   :tosUrl "<your-tos-url>"

   ;; Why is this one a function and not data? Who knows!
   :privacyPolicyUrl #(js/window.location.assign "<your-privacy-policy-url>")

   ;; querySelector of the DOM element that firebaseUI-auth should be rendered within
   :container-selector "#firebaseui-auth-container"})

(def config-keys
  [:signInSuccessUrl
   :signInOptions
   :tosUrl
   :privacyPolicyUrl])
