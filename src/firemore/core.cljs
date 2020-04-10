(ns firemore.core
  (:require
   [cljs.core.async :as async]
   [firemore.config :as config]
   [firemore.authentication :as authentication]
   [firemore.finalizing-buffer :as finalizing-buffer]
   [firemore.firestore :as firestore]
   [firemore.firebase :as firebase]
   [firemore.hydrator :as hydrator])
  (:refer-clojure :exclude [get])
  (:require-macros
   [cljs.core.async.macros :refer [go-loop go]]))

(enable-console-print!)

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
  "Returns a javascript object from the firemore `document` (a clojure map)."
  ([document]
   (-> document
       throw-if-unsupported
       firestore/jsonify)))

;; database

(defn get
  "Get the document at `reference` in the Firestore database.

  Returns a channel. A map representing the data at this location will be put
  on this channel. The channel will then be closed.

  Note:
  channel -> `clojure.core.async/chan`
  put    -> `clojure.core.async/put!`
  closed  -> `clojure.core.async/close!`"
  [reference & [options]]
  (firestore/get-db reference options))

(defn watch
  "Watch the document at `reference` in the Firestore database.

  Returns a channel. A map representing the data at this location will be put
  on this channel. As the document at reference is updated through time, the
  channel will put! the newest value of the document upon the channel.

  Important: close! the channel to clean up the state machine feeding this
  channel. Failure to close the channel will result in a memory leak.

  Note:
  channel -> `clojure.core.async/chan`
  put     -> `clojure.core.async/put!`
  closed  -> `clojure.core.async/close!`"
  [reference & [options]]
  (let [{:keys [c unsubscribe]} (firestore/listen reference options)
        opts {:on-close #(unsubscribe)}
        buffer (finalizing-buffer/create 1 opts)
        finalizing-chan (async/chan buffer)]
    (-> c async/mult (async/tap finalizing-chan))
    finalizing-chan))

(defn push!
  "Adds the `document` to collection `reference` within the Firestore database.

  Returns a channel. Creates a new id for `document`. Either
  {:id <document-id>} or {:error <error-msg>} will then be placed upon the
  channel. The channel will then be closed."
  [reference document & [options]]
  (firestore/add-db! reference document options))

(defn write!
  "Writes the `document` to `reference` within the Firestore database.

  Returns a channel. Overwrites the document at `reference` with `document`.
  Iff an error occurs when writing document to Firestore, then the error will
  be put upon the channel. The channel will then be closed.

  Note:
  channel -> `clojure.core.async/chan`
  put     -> `clojure.core.async/put!`
  closed  -> `clojure.core.async/close!`"
  [reference document & [options]]
  (firestore/set-db! reference document options))

(defn merge!
  "Merges `document` into the document at `reference` within the Firestore database.

  Returns a channel. Updates (merges) the document at `reference` with `document`.
  Iff an error occurs when writing `document` to Firestore, then the error will be put
  upon the channel. The channel will then be closed.

  Note:
  channel -> `clojure.core.async/chan`
  put     -> `clojure.core.async/put!`
  closed  -> `clojure.core.async/close!`"
  [reference document & [options]]
  (firestore/update-db! reference document options))

(defn delete!
  "Deletes the document at `reference` within the Firestore database.

  Returns a channel. Iff an error occurs when deleting reference from Firestore,
  then the error will be put upon the channel. The channel will then be closed.

  Note:
  channel -> `clojure.core.async/chan`
  put     -> `clojure.core.async/put!`
  closed  -> `clojure.core.async/close!`"
  [reference & [options]]
  (firestore/delete-db! reference options))

(defn transact! [update-fx]
  (firestore/transact-db! update-fx))

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
  ;; TODO: What to do with this?
  ;; authentication/user-chan
  )

(defn user-atom
  "Return the atom that reflects the state of currently logged in user"
  []
  authentication/user-atom)

(defn user
  "Returns the last value returned from user-chan."
  []
  @(user-atom))

(defn login-anonymously! []
  "Log in a new anonymous user; noop if already logged in"
  (authentication/login-anonymously!))

(defn uid
  "Returns a channel that will have a uid put! upon it'

  If you are currently logged in, uid will be the uid of the currently logged
  in user. If you are not currently logged in, client will login with the
  anonymous user, and then the uid will be the uid of the anonymous user.

  Note:
  channel -> `clojure.core.async/chan`
  put!    -> `clojure.core.async/put!`"
  []
  (authentication/uid))

(defn logout! []
  "Log out any currently logged in user."
  (authentication/logout!))

(defn delete-user!
  "Deletes the currently logged in user from Firestore.

  This removes all sign-in providers for this user, as well as deleting the data in the
  user information map returned by (get-user-atom). Note that this does NOT delete
  information relating to the user from the actual Firestore database."
  [])

;; watchers

(defn add!
  "Sync the current value of `reference` at `path` within the `atm`

  atm - A clojure atom.
  path - a vector location within the `atm` where the Firestore `reference` will be written.
  reference - a reference to a location in Firestore.

  Note that the the {path reference} will show up under the :firemore key, and the
  {path reference-value} will show up under the :firemore key in `atm`."
  [atm path reference]
  (hydrator/add! atm path reference))

(defn subtract!
  "Remove the `path` from the `atm`"
  [atm path]
  (hydrator/subtract! atm path))

(defn watch-user
  "Add functionality to atom `atm` so that `:user` reflects latest value from `get-user`"
  [atm])

(defn unwatch-user
  "Removes functionality on `atm` that may have been added by `watch-user`"
  [atm])
