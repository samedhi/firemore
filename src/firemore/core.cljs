(ns firemore.core
  (:require
   [cljs.core.async :as async]
   [firemore.config :as config]
   [firemore.authentication :as authentication]
   [firemore.finalizing-buffer :as finalizing-buffer]
   [firemore.firestore :as firestore]
   [firemore.firebase :as firebase]))

;; interop

(def supported-types [string? int? float? boolean? nil? inst?])

(defn throw-if-unsupported [m]
  (some->> m
           vals
           (remove (fn [v] (some #(% v) supported-types)))
           first
           (ex-info "Unsupported Data")
           throw)
  m)

(defn fire->clj
  "Returns the clojure form of the `js-object` document from Firestore."
  [js-object]
  (-> js-object
      firestore/clojurify
      throw-if-unsupported))

(defn clj->fire
  "Returns a javascript object from the firemore `document` (a map)."
  ([document]
   (-> document
       throw-if-unsupported
       firestore/jsonify)))

;; references

(defn ref [ks]
  (->> ks
       (mapv name)
       firestore/ref))

;; database

(defn grab
  "Get the document at `reference` in the Firestore database.

  Returns a channel. If a document exist at `reference`, it will be put! upon the
  channel. If no document exist at `reference`, then `:firemore/no-document` will be
  put! on the channel. The channel will then be closed.

  Note:
  channel -> `clojure.core.async/chan`
  put!    -> `clojure.core.async/put!`
  closed  -> `clojure.core.async/close!`"
  [reference]
  (-> reference ref firestore/get-db))

(defn watch
  "Watch the document at `reference` in the Firestore database.

  Returns a channel. If a document exist at `reference`, it will be put! upon
  the channel. If no document exist at reference, then `:firemore/no-document` will
  be put! on the channel. As the document at reference is updated through
  time, the channel will put! the newest value of the document (if it exist)
  or :firemore/no-document (if it does not) upon the channel.

  Important: close! the channel to clean up the state machine feeding this
  channel. Failure to close the channel will result in a memory leak.

  Note:
  channel -> `clojure.core.async/chan`
  put!    -> `clojure.core.async/put!`
  closed  -> `clojure.core.async/close!`"
  [reference]
  (let [{:keys [chan unsubscribe]} (-> reference ref firestore/listen-db)
        opts {:on-close #(do (async/close! chan) (unsubscribe))}
        buffer (finalizing-buffer/create 1 opts)
        finalizing-chan (async/chan buffer)]
    (-> chan async/mult (async/tap finalizing-chan))
    finalizing-chan))

(defn write!
  "Writes the `document` to `reference` within the Firestore database.

  Returns a channel. Overwrites the document at `reference` with `document`.
  Iff an error occurs when writing m to Firestore, then the error will be put!
  upon the channel. The channel will then be closed.

  Note:
  channel -> `clojure.core.async/chan`
  put!    -> `clojure.core.async/put!`
  closed  -> `clojure.core.async/close!`"
  [reference document]
  (-> reference ref (firestore/set-db! document)))

(defn merge!
  "Merges `document` into the document at `reference` within the Firestore database.

  Returns a channel. Updates (merges) the document at `reference` with `document`.
  Iff an error occurs when writing `document` to Firestore, then the error will be put!
  upon the channel. The channel will then be closed.

  Note:
  channel -> `clojure.core.async/chan`
  put!    -> `clojure.core.async/put!`
  closed  -> `clojure.core.async/close!`"
  [reference document]
  (-> reference ref (firestore/update-db! document)))

(defn delete!
  "Deletes the document at `reference` within the Firestore database.

  Returns a channel. Iff an error occurs when deleting reference from Firestore,
  then the error will be put! upon the channel. The channel will then be closed.

  Note:
  channel -> `clojure.core.async/chan`
  put!    -> `clojure.core.async/put!`
  closed  -> `clojure.core.async/close!`"
  [reference]
  (-> reference ref firestore/delete-db!))

;; authentication

(defn user-chan
  "Returns a channel. Will put! user map or :firemore/no-user as user state changes..

    {:uid <application_unique_id>
   :email <user_email_address>
   :name <user_identifier>
   :photo <url_to_a_photo_for_this_user>}

  Note: :uid will always be present. :email, :name, :photo may be present depending
  on sign-in provider and/or whether you have set their values."
  []
  authentication/user-chan)

(defn user
  "Returns the last value returned from user-chan."
  []
  @authentication/user-atom)


(defn logout!
  "Log out the currently logged in user (if any)."
  [])

(defn login-anonymously!
  "Log out any existing user, then log in a new anonymous user."
  []
  authentication/login-anonymously!)

(defn delete-user!
  "Deletes the currently logged in user from Firestore.

  This removes all sign-in providers for this user, as well as deleting the data in the
  user information map returned by (get-user-atom). Note that this does NOT delete
  information relating to the user from the actual Firestore database."
  [])

;; watchers

(defn hydrate
  "Add functionality to atom `atm` to allow observation of the Firestore database.

  Returns nil. Noop if atom is already hydrated. Adds a watch that causes atom to
  automatically sync its :paths and :references root keys. :paths should be a
  key value pair where the key is a path and the value is a reference. :paths can
  have keys added and removed. :references will throw a error if you attempt to
  modify them.

  Note:
  atom -> https://clojure.org/reference/atoms"
  [atm])

(defn unhydrate
  "Removes functionality on `atm` that may have been added by `hydrate`."
  [atm])

(defn watch-user
  "Add functionality to atom `atm` so that `:user` reflects latest value from `get-user`"
  [atm])

(defn unwatch-user
  "Removes functionality on `atm` that may have been added by `watch-user`"
  [atm])
