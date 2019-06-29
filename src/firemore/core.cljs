(ns firemore.core)

(defn grab
  "Get the document at `reference` in the Firestore database.

  Returns a channel. If a document exist at `reference`, it will be put! upon the
  channel. If no document exist at `reference`, then `:firemore/no-document` will be
  put! on the channel. The channel will then be closed.

  Note:
  channel -> `clojure.core.async/chan`
  put!    -> `clojure.core.async/put!`
  closed  -> `clojure.core.async/close!`"
  [reference] )

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
  [reference])

(defn write!
  "Writes the `document` to `reference` within the Firestore database.

  Returns a channel. Overwrites the document at `reference` with `document`.
  Iff an error occurs when writing m to Firestore, then the error will be put!
  upon the channel. The channel will then be closed.

  Note:
  channel -> `clojure.core.async/chan`
  put!    -> `clojure.core.async/put!`
  closed  -> `clojure.core.async/close!`"
  [reference document])

(defn merge!
  "Merges `document` into the document at `reference` within the Firestore database.

  Returns a channel. Updates (merges) the document at `reference` with `document`.
  Iff an error occurs when writing `document` to Firestore, then the error will be put!
  upon the channel. The channel will then be closed.

  Note:
  channel -> `clojure.core.async/chan`
  put!    -> `clojure.core.async/put!`
  closed  -> `clojure.core.async/close!`"
  [reference document])

(defn delete!
  "Deletes the document at `reference` within the Firestore database.

  Returns a channel. Iff an error occurs when deleting reference from Firestore,
  then the error will be put! upon the channel. The channel will then be closed.

  Note:
  channel -> `clojure.core.async/chan`
  put!    -> `clojure.core.async/put!`
  closed  -> `clojure.core.async/close!`"
  [reference])

(defn hydrate
  "Adds functionality to atom `atm` to allow observation of the Firestore database.

  Returns nil. Noop if atom is already hydrated. Adds a watch that causes atom to
  automatically sync its :paths and :references root keys. :paths should be a
  key value pair where the key is a path and the value is a reference. :paths can
  have keys added and removed. :references will throw a error if you attempt to
  modify them.

  Note:
  atom -> https://clojure.org/reference/atoms"
  [atm])

(defn unhydrate
  "Removes functionality on `atm` that may have been added by `firemore/hydrate`.

  Returns nil. Noop if atom is already unhydrated."
  [atm])
