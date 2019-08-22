(ns firemore.finalizing-buffer
  (:require
   [cljs.core.async.impl.buffers :as buffers]
   [cljs.core.async.impl.protocols :as protocols]))

(deftype FinalizingBuffer [buf n opts]
  protocols/Buffer
  (full? [this]
    (== (.-length buf) n))
  (remove! [this]
    (.pop buf))
  (add!* [this itm]
    (.unbounded-unshift buf itm)
    this)
  (close-buf! [this]
    (some-> opts :on-close (apply [this])))
  cljs.core/ICounted
  (-count [this]
    (.-length buf)))

(defn create
  ([n] (create n {}))
  ([n opts]
   (FinalizingBuffer.
    (buffers/ring-buffer n)
    n
    opts)))
