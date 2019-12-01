(ns firemore.firestore-macros)

(defmacro transact-db!
  "Reads 'bindings from database, executes body within transaction."
  [bindings & body]
  (let [transaction (gensym "transaction_")]
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
        `(.catch
          (.then
           (.runTransaction
            (.firestore firemore.firestore/FB)
            (fn [~transaction]
              ~acc))
           (fn [success#] (println "SUCCESS -> " success#)))
          (fn [error#] (println "FAIL -> " error#)))))))
