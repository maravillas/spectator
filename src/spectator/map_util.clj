(ns spectator.map-util)

(defn merge-with-metadata [& maps]
  "Returns a map that consists of the rest of the maps and their metadata
  conj-ed onto the first."
  (when (some identity maps)
    (let [meta (apply merge (map meta maps))]
      (with-meta (apply merge maps) meta))))

(defn map-diff
  "Extracts the changes from m1 to m2 as a map."
  [m1 m2]
  (let [keys (set (concat (keys m1) (keys m2)))]
    (into {} (map (fn [k] [k (k m2)])
		   (remove nil? (map (fn [key]
				       (when (not= (key m1) (key m2)) key))
				     keys))))))
(defn map-subset?
  "Determines whether m1 is a subset of m2."
  [m1 m2]
  (or (= (count m1) 0)
      (every? #(and (contains? m2 %1)
		  (= (%1 m1) (%1 m2)))
	    (keys m1))))