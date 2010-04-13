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

(defn- run-updaters [old-map updates updaters]
  (let [new-map (merge-with-meta old-map updates)]
    (if (nil? (first updaters))
      updates
      (let [new-updates (merge-with-meta updates ((first updaters) old-map new-map))]
	(if (vetoed? new-updates)
	  (veto)
	  (recur old-map new-updates (rest updaters)))))))

(defn- updaters-for-keys [updaters keys]
  (distinct (mapcat #(%1 updaters) keys)))

(defn- notify-updaters
  ([old-map updates]
     (notify-updaters old-map updates updates))
  
  ([old-map updates all-updates]
     (let [updaters     (updaters-for-keys (updaters old-map) (keys updates))
	   next-updates (run-updaters old-map updates updaters)
	   new-map      (merge-with-meta old-map updates)
	   diff         (with-meta (map-diff updates next-updates)
			  (merge (meta updates) (meta next-updates)))
	   changed?     (not (map-subset? next-updates all-updates))]
       (cond
	(vetoed? next-updates) {}
	changed?               (recur new-map diff (merge-with-meta all-updates next-updates))
	true                   all-updates))))

(defn- notify-observers
  [old-map new-map keys agent]
  (let [updaters (updaters-for-keys (observers old-map) keys)]
    (when (and updaters agent)
      (send agent (fn [state]
		    (doseq [updater updaters]
		      (updater old-map new-map)))))))

(defn- redundant-update? [map key value]
  (and (contains? map key)
       (= (key map) value)))

(defn- combine-updater-updates [map updates memo]
  (let [memo (merge memo {:initial-changes updates})]
    (without-memo (notify-updaters map (with-memo updates memo)))))

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
	true       (let [diff (combine-updater-updates map updates memo)
			 new-map (merge-with-meta map diff)]
		     (notify-observers map new-map (keys diff) agent)
		     new-map)))))

(defn touch
  "Runs the appropriate updaters and observers on the map without modifying its
  value."
  ([map key]
     (touch map (agent nil) key))
  ([map agent & keys]
     (let [kvs (merge (apply hash-map (interleave keys (take (count keys) (repeat nil))))
		      (select-keys map keys))
	   diff (combine-updater-updates map kvs {})
	   new-map (merge-with-meta map diff)]
       (notify-observers map new-map (clojure.core/keys diff) agent)
       new-map)))

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