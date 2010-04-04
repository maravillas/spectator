(ns spectator.map-util)

(defn map-diff [m1 m2]
  (let [keys (set (concat (keys m1) (keys m2)))]
    (remove nil? (map (fn [key]
			(when (not= (key m1) (key m2)) key))
		      keys))))
