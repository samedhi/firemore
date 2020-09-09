(ns firemore.config
  (:require
   [clojure.string :as string]
   [goog.object :as goog.object]))

(def TIMESTAMP :firemore/timestamp)

(def NO_DOCUMENT :firemore/no-document)

(def LOADING :firemore/loading)

(def default-firebase-config
  {:api-key    "AIzaSyAEEGdlXMkrxbF-OWbsDffCSKMogeiRvfA"
   :project-id "inferno-8d188"})
