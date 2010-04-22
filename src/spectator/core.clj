(ns spectator.core
  (:require [spectator.multimap :as multimap])
  (:use [spectator map-util collection-util]
	[clojure.contrib logging]))

(declare memo with-memo veto)

(defn- updaters [map]
  (:updaters (meta map)))

(defn- global-updaters [map]
  (:global-updaters (meta map)))

(defn- observers [map]
  (:observers (meta map)))

(defn- global-observers [map]
  (:global-observers (meta map)))

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
    (if (empty? updaters)
      (with-memo updates memo)
      (let [update ((first updaters) old-map (with-memo new-map memo))
	    [new-updates new-memo] (merge-updates updates memo update)]
	(trace (str "Result from updater " (first updaters) ": " update))
	(if (vetoed? update)
	  (veto)
	  (recur old-map new-updates new-memo (rest updaters)))))))

(defn- watchers-for-keys [watchers keys]
  (distinct (mapcat #(%1 watchers) keys)))

(defn- notify-updaters
  ([old-map updates memo]
     (notify-updaters old-map updates memo updates))
  
  ([old-map updates memo all-updates]
     (let [updaters         (watchers-for-keys (updaters old-map) (keys updates))
	   all-updaters     (into updaters (global-updaters old-map))
	   updater-results  (run-updaters old-map updates memo all-updaters)
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
  (let [observers (watchers-for-keys (observers old-map) keys)
	all-observers (into observers (global-observers old-map))]
    (when (and all-observers agent)
      (send agent (fn [state]
		    (doseq [observer all-observers]
		      (trace (str "Running observer " observer))
		      (observer old-map (with-memo new-map memo))))))))

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

(defn- alter-watchers
  ([map updater-key op f]
     (let [updaters (or (updater-key (meta map)) #{})]
       (with-meta map (merge (meta map) {updater-key (op updaters f)}))))
  ([map updater-key op f & keys]
      (let [funcs (take (count keys) (repeat f))
	    kvs (interleave keys funcs)
	    updaters (updater-key (meta map))]
	(with-meta map (merge (meta map) {updater-key (apply op updaters kvs)})))))

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
     (debug (str "Updating map with " updates (when silent " (silently)")))
     
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
     (debug (str "Touching " keys))
     
     (let [kvs (merge (apply hash-map (alternate-with keys nil))
		      (select-keys map keys))
	   diff (combine-updates map kvs memo false)
	   new-map (merge-with-meta map diff)]
       (notify-observers map new-map (spectator.core/memo diff) (clojure.core/keys diff) agent)
       (without-memo new-map))))

(defn add-updater
  "Adds an updater that is run when the key's value changes. f should be a
  function taking two arguments, the old map and the new map, and should return
  a map of the updates to apply to the map.

  If no keys are specified, the updater is run when any key in the map changes."
  ([map f]
     (debug (str "Adding updater " f " to all keys"))
     (alter-watchers map :global-updaters conj f))
  ([map f & keys]
     (debug (str "Adding updater " f " to " keys))
     (apply alter-watchers map :updaters multimap/add f keys)))

(defn remove-updater
  "Removes an updater from the specified keys. If no keys are specified, the
  updater is removed from those updaters watching any key."
  ([map f]
     (debug (str "Removing updater " f " from all keys"))
     (alter-watchers map :global-updaters disj f))
  ([map f & keys]
      (debug (str "Removing updater " f " to " keys))
      (apply alter-watchers map :updaters multimap/del f keys)))

(defn add-observer
  "Adds an observer that is run when the key's value changes. Observers
  are run in an agent after all updaters have completed. Return values from
  observers are ignored.

  f should be a function taking two arguments, the old map and the new map.

  If no keys are specified, the observer is run when any key in the map changes."
  ([map f]
     (debug (str "Adding observer " f " to all keys"))
     (alter-watchers map :global-observers conj f))
  ([map f & keys]
      (debug (str "Adding observer " f " to " keys))
      (apply alter-watchers map :observers multimap/add f keys)))

(defn remove-observer
  "Removes an observer from the specified keys. If no keys are specified, the
   observer is removed from those observers watching any key."
  ([map f]
     (debug (str "Removing observer " f " from all keys"))
     (alter-watchers map :global-observers disj f))
  ([map f & keys]
      (debug (str "Removing observer " f " to " keys))
      (apply alter-watchers map :observers multimap/del f keys)))