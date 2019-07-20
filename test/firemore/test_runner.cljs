(ns firemore.test-runner
  (:require
   [cljs.test :as t :include-macros true]
   [cljs-test-display.core]
   [firemore.core-test]
   [firemore.firebase-test]
   [firemore.firestore-test]))

(enable-console-print!)

(defn test-run []
  (t/run-tests
   (cljs-test-display.core/init! "test")
   'firemore.core-test
   'firemore.firebase-test
   'firemore.firestore-test
   ))

(test-run)

