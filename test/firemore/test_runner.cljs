(ns firemore.test-runner
  (:require
   [cljs.test :as t :include-macros true]
   [doo.runner :refer-macros [doo-all-tests]]
   [firemore.core-test]
   [firemore.firestore-test]))

(enable-console-print!)

(doo-all-tests)
