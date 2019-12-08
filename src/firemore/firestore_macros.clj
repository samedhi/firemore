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
        ret-chan (gensym "return_")]
    (loop [[[sym expr :as tuple] & remaining] (reverse (partition 2 bindings)) 
           acc `((fn []
                   (binding [firemore.firestore/*transaction* ~transaction]
                     ~@body)))]
      (if tuple
        (recur
         remaining
         `(.then
           (.get ~transaction (firemore.firestore/ref ~expr))
           (fn [~sym]
             (let [~sym (firemore.firestore/doc-upgrader ~sym)]
               ~acc))))
        `(let [~ret-chan (async/chan)]
           (.catch
            (.then
             (.runTransaction
              (.firestore firemore.firestore/FB)
              (fn [~transaction] ~acc))
             (fn [success#] (put-close! ~ret-chan [success# nil])))
            (fn [error#] (put-close! ~ret-chan [nil error#])))
           ~ret-chan)))))
