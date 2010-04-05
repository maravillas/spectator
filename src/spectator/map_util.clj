(ns spectator.map-util)

(defn- merge-with-metadata [& maps]
  (when (some identity maps)
    (let [meta (apply merge (map meta maps))]
      (with-meta (apply merge maps) meta))))

(defn map-diff [m1 m2]
  (let [keys (set (concat (keys m1) (keys m2)))]
    (into {} (map (fn [k] [k (k m2)])
		   (remove nil? (map (fn [key]
				       (when (not= (key m1) (key m2)) key))
				     keys))))))
