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

(defn test-run []
  (t/run-tests
   (cljs-test-display.core/init! "test")
   'firemore.authentication-test
   'firemore.core-test
   'firemore.firebase-test
   'firemore.firestore-test
   'firemore.hydrator-test
   ))

(def ^:export exit_code 0)

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (println "This even ran")
  (println m)
  (when-not (cljs.test/successful? m)
    (def exit_code 1)))

(defn ^:export run []
  (test-run))

