(ns firemore.firestore-macros
  (:require
   [cljs.core.async :as async]))

(defmacro put-close! [c v]
  `(do (async/put! ~c ~v)
       (async/close! ~c)))

(defmacro transact-db!
  "Reads 'bindings from database, executes body within transaction."
  [bindings & body]
  (let [transaction (gensym "transaction_")
        ret-chan (gensym "return_")
        reads (gensym "reads_")]
    (loop [[[sym expr :as tuple] & remaining] (reverse (partition 2 bindings)) 
           acc `(binding [firemore.firestore/*transaction* ~transaction
                          firemore.firestore/*transaction-unwritten-docs* ~reads]
                  (let [result# ~@body]
                    (doseq [path# (deref ~reads)]
                      (firemore.firestore/update-db! path# {}))
                    result#))]
      (if tuple
        (recur
         remaining
         `(let [path# ~expr]
            (.then
             (.get ~transaction (firemore.firestore/ref path#))
             (fn [doc#]
               (swap! ~reads conj path#)
               (let [~sym (firemore.firestore/doc-upgrader doc#)]
                 ~acc)))))
        `(let [~ret-chan (async/chan)
               ~reads (atom #{})]
           (.catch
            (.then
             (.runTransaction
              (.firestore firemore.firestore/FB)
              (fn [~transaction] ~acc))
             (fn [success#] (put-close! ~ret-chan [success# nil])))
            (fn [error#] (put-close! ~ret-chan [nil error#])))
           ~ret-chan)))))
