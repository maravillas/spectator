(ns spectator.core
  (:require [spectator.multimap :as multimap])
  (:use [spectator.map-util]
	[clojure.contrib swing-utils logging]))

(declare memo with-memo veto)

(defn- watchers [map]
  (:watchers (meta map)))

(defn- without-memo [map]
  (with-meta map (dissoc (meta map) :memo)))

(defn- vetoed? [map]
  (:veto (memo map)))

(defn- run-watchers [old-map updates watchers]
  (let [new-map (merge-with-meta old-map updates)]
    (if (nil? (first watchers))
      updates
      (let [new-updates (merge-with-meta updates ((first watchers) old-map new-map))]
	(if (vetoed? new-updates)
	  (veto)
	  (recur old-map new-updates (rest watchers)))))))

(defn- watchers-for-keys [map keys]
  (let [watchers (watchers map)]
    (distinct (mapcat #(%1 watchers) keys))))

(defn- notify-watchers
  ([old-map updates]
     (notify-watchers old-map updates updates))
  
  ([old-map updates all-updates]
     (let [watchers     (watchers-for-keys old-map (keys updates))
	   next-updates (run-watchers old-map updates watchers)
	   new-map      (merge-with-meta old-map updates)
	   diff         (with-meta (map-diff updates next-updates)
			  (merge (meta updates) (meta next-updates)))
	   changed?     (not (map-subset? next-updates all-updates))]
       (cond
	(vetoed? next-updates) {}
	changed?               (recur new-map diff (merge-with-meta all-updates next-updates))
	true                   all-updates))))

(defn- redundant-update? [map key value]
  (and (contains? map key)
       (= (key map) value)))

(defn- alter-watches [map op f & keys]
  (let [funcs (take (count keys) (repeat f))
	kvs (interleave keys funcs)
	watchers (watchers map)]
    (with-meta map {:watchers (apply op watchers kvs)})))

;;; Public API

(defn memo
  "Returns the memos attached to a map."
  [map]
  (:memo (meta map)))

(defn with-memo
  "Returns a new map with new-memo merged with the map's memo."
  [map new-memo]
  (let [current-memo (memo map)
	new-memo (merge current-memo new-memo)]
    (with-meta map (merge (meta map) {:memo new-memo}))))

(defn veto
  "Marks an update as vetoed, aborting its change and any changes caused by
  subsequent watchers. Watchers after the vetoing watcher are not executed."
  []
  (with-memo {} {:veto true}))

(defn update 
  "Returns a new map that contains updated key-value mappings, and executes any
  watchers for the corresponding keys. If silent is true, watchers are not
  notified (defaults to false). Optionally supplies an additional memo to the
  watchers (defaults to {})."
  ([map updates]
     (update map updates false))

  ([map updates silent]
     (update map updates silent {}))

  ([map updates silent memo]
     (let [memo (merge memo {:initial-changes updates})
	   redundant? (some #(apply redundant-update? map %1) updates)]
       (cond
	redundant? map
	silent     (merge-with-meta map updates)
	true       (merge map (without-memo
			       (notify-watchers map
						(with-memo updates memo))))))))

(defn touch
  "Runs handlers on the map without modifying its value."
  [map & keys]
  (let [watchers (watchers-for-keys map keys)]
    (run-watchers map map watchers)))

(defn watch-keys
  "Adds a watch that is run only when the key's value changes. f should be a
  function taking two arguments: the old map and the new map."
  [map f & keys]
  (apply alter-watches map multimap/add f keys))

(defn unwatch-keys
  "Removes a watch from the specified keys."
  [map f & keys]
  (apply alter-watches map multimap/del f keys))