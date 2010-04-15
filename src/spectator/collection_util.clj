(ns spectator.collection-util)

(defn alternate-with [coll value]
  (interleave coll (take (count coll) (repeat value))))