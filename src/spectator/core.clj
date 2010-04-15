(ns spectator.core
  (:require [spectator.multimap :as multimap])
  (:use [spectator.map-util]
	[clojure.contrib logging]))

(declare memo with-memo veto)

(defn- updaters [map]
  (:updaters (meta map)))

(defn- observers [map]
  (:observers (meta map)))

(defn- without-memo [map]
  (with-meta map (dissoc (meta map) :memo)))

(defn- vetoed? [map]
  (:veto (memo map)))

(defn- merge-updates [original-map original-memo updates]
  (let [merged-map (merge original-map updates)
	merged-memo (merge original-memo (memo updates))]
    [merged-map merged-memo]))

(defn- run-updaters [old-map updates memo updaters]
  (let [new-map (merge old-map updates)]
    (if (nil? (first updaters))
      (with-memo updates memo)
      (let [update ((first updaters) old-map (with-memo new-map memo))
	    [new-updates new-memo] (merge-updates updates memo update)]
	(if (vetoed? update)
	  (veto)
	  (recur old-map new-updates new-memo (rest updaters)))))))

(defn- updaters-for-keys [updaters keys]
  (distinct (mapcat #(%1 updaters) keys)))

(defn- notify-updaters
  ([old-map updates memo]
     (notify-updaters old-map updates memo updates))
  
  ([old-map updates memo all-updates]
     (let [updaters         (updaters-for-keys (updaters old-map) (keys updates))
	   updater-results  (run-updaters old-map updates memo updaters)
	   next-map         (merge old-map updates)
	   next-updates     (map-diff updates updater-results)
	   next-memo        (merge memo (spectator.core/memo updater-results))
	   next-all-updates (merge all-updates updater-results)
	   vetoed?          (vetoed? updater-results)
	   changed?         (not (map-subset? updater-results all-updates))]
       (cond
	vetoed?  {}
	changed? (recur next-map next-updates next-memo next-all-updates)
	true     (with-memo all-updates next-memo)))))

(defn- notify-observers
  [old-map new-map memo keys agent]
  (let [updaters (updaters-for-keys (observers old-map) keys)]
    (when (and updaters agent)
      (send agent (fn [state]
		    (doseq [updater updaters]
		      (updater old-map (with-memo new-map memo))))))))

(defn- redundant-update? [map key value]
  (and (contains? map key)
       (= (key map) value)))

(defn- combine-updates
  ([map updates memo]
     (combine-updates map updates memo true))
  ([map updates memo add-initial-changes]
     (let [memo (if add-initial-changes
		  (merge memo {:initial-changes updates})
		  memo)]
       (notify-updaters map updates memo))))

(defn- alter-watchers [map updater-key op f & keys]
  (let [funcs (take (count keys) (repeat f))
	kvs (interleave keys funcs)
	updaters (updater-key (meta map))]
    (with-meta map (merge (meta map) {updater-key (apply op updaters kvs)}))))

;;;;;; Public API ;;;;;;

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
  subsequent updaters. Updaters after the vetoing updater are not executed."
  []
  (with-memo {} {:veto true}))

(defn update 
  "Returns a new map that contains updated key-value mappings, and executes any
  updaters for the corresponding keys. If silent is true, updaters are not
  notified (defaults to false). Optionally supplies an additional memo to the
  updaters (defaults to {})."
  ([map updates]
     (update map updates false))

  ([map updates silent]
     (update map updates silent {}))

  ([map updates silent memo]
     (update map updates silent memo (agent nil)))
  
  ([map updates silent memo agent]
     (let [redundant? (some #(apply redundant-update? map %1) updates)]
       (cond
	redundant? map
	silent     (merge-with-meta map updates)
	true       (let [diff (combine-updates map updates memo)
			 new-map (merge map diff)]
		     (notify-observers map new-map (spectator.core/memo diff) (keys diff) agent)
		     new-map)))))

(defn touch
  "Runs the appropriate updaters and observers on the map without modifying its
  value."
  ([map key]
     (touch map {} key))
  ([map memo key]
     (touch map {} (agent nil) key))
  ([map memo agent & keys]
     (let [kvs (merge (apply hash-map (interleave keys (take (count keys) (repeat nil))))
		      (select-keys map keys))
	   diff (combine-updates map kvs memo false)
	   new-map (merge-with-meta map diff)]
       (notify-observers map new-map (spectator.core/memo diff) (clojure.core/keys diff) agent)
       (without-memo new-map))))

(defn add-updater
  "Adds an updater that is run when the key's value changes. f should be a
  function taking two arguments, the old map and the new map, and should return
  a map of the updates to apply to the map."
  [map f & keys]
  (apply alter-watchers map :updaters multimap/add f keys))

(defn remove-updater
  "Removes an updater from the specified keys."
  [map f & keys]
  (apply alter-watchers map :updaters multimap/del f keys))

(defn add-observer
  "Adds an observer that is run when the key's value changes. Observers
  are run in an agent after all updaters have completed. Return values from
  observers are ignored.

  f should be a function taking two arguments, the old map and the new map."
  [map f & keys]
  (apply alter-watchers map :observers multimap/add f keys))

(defn remove-observer
  "Removes an observer from the specified keys."
  [map f & keys]
  (apply alter-watchers map :observers multimap/del f keys))