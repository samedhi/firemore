(ns firemore.test-runner
  (:require
   [cljs.test :as t :include-macros true]
   [firemore.core-test]
   [firemore.firestore-test]))

(enable-console-print!)

(t/run-all-tests)
