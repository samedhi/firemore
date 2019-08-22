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
   ;; 'firemore.authentication-test
   ;; 'firemore.core-test
   ;; 'firemore.firebase-test
   ;; 'firemore.firestore-test
   'firemore.hydrator-test
   ))

(test-run)

