(ns firemore.test-runner
  (:require
   [cljs.test :as t :include-macros true]
   [cljs-test-display.core]
   [firemore.authentication-test]
   [firemore.core-test]
   [firemore.firebase-test]
   [firemore.firestore-test]
   [firemore.hydrator-test]))

(enable-console-print!)

(def successful (atom false))

(def complete (atom false))

(defmethod t/report [::t/default :end-run-tests] [m]
  (when (cljs.test/successful? m)
    (reset! successful true))
  (reset! complete true))

(defn test-run [ui?]
  (t/run-tests
   (when ui? (cljs-test-display.core/init! "test"))
   'firemore.authentication-test
   'firemore.core-test
   'firemore.firebase-test
   'firemore.firestore-test
   'firemore.hydrator-test))

(defn ^:export is_successful []
  @successful)

(defn ^:export is_complete []
  @complete)

(defn ^:export run []
  (test-run true))

(defn ^:export run_without_ui []
  (test-run false))
