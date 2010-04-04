(ns #^{:doc "A multimap is a map that permits multiple values for each
  key. This implementation represents multimaps as maps containing
  distinct seqs. The order of values in the seqs is the order they
  were added to the multimap.

  Original source: http://paste.lisp.org/display/89840"}
  spectator.multimap
  (:use [clojure.set :only (union)]))

(defn add
  "Adds key-value pairs to the multimap."
  ([mm k v]
     (assoc mm k (distinct (concat (get mm k []) [v]))))
  ([mm k v & kvs]
     (apply add (add mm k v) kvs)))

(defn del
  "Removes key-value pairs from the multimap."
  ([mm k v]
     (assoc mm k (remove #(= %1 v) (get mm k))))
  ([mm k v & kvs]
     (apply del (del mm k v) kvs)))