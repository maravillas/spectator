(ns spectator.core
  (:require [spectator.multimap :as multimap])
  (:use [spectator.map-util]
	[clojure.contrib swing-utils logging]))

(defn- watchers [context]
  (:watchers (meta context)))

(defn- without-memo [context]
  (with-meta context (dissoc (meta context) :memo)))

(defn- run-watchers [old-context updates watchers]
  (let [new-context (merge-with-meta old-context updates)]
    (if (first watchers)
      (recur old-context
	     (merge-with-meta updates ((first watchers) old-context new-context))
	     (rest watchers))
      updates)))

(defn- watchers-for-keys [context keys]
  (let [watchers (watchers context)]
    (distinct (mapcat #(%1 watchers) keys))))

(defn- notify-watchers
  ([old-context updates]
     (notify-watchers old-context updates updates))
  
  ([old-context updates all-updates]
     (let [watchers     (watchers-for-keys old-context (keys updates))
	   next-updates (run-watchers old-context updates watchers)
	   new-context  (merge-with-meta old-context updates)
	   diff         (with-meta (map-diff updates next-updates)
			  (merge (meta updates) (meta next-updates)))]
       (if (not (map-subset? next-updates all-updates))
	 (recur new-context diff (merge-with-meta all-updates next-updates))
	 all-updates))))

(defn- redundant-update? [context key value]
  (and (contains? context key)
       (= (key context) value)))

(defn- alter-watches [context op f & keys]
  (let [funcs (take (count keys) (repeat f))
	kvs (interleave keys funcs)
	watchers (watchers context)]
    (with-meta context {:watchers (apply op watchers kvs)})))

;;; Public API

(defn with-memo
  "Merges the specified key-value pairs with the context's memo."
  [context memo]
  (with-meta context (merge (meta context) {:memo memo})))

(defn memo
  "Retrieves the memos attached to a context."
  [context]
  (:memo (meta context)))

(defn update 
  "Updates the context with a new value. If silent is true, watchers are not
  notified (defaults to false)."
  ([context updates]
     (update context updates false))

  ([context updates silent]
     (let [initial-changes {:initial-changes updates}
	   redundant? (some #(apply redundant-update? context %1) updates)]
       (cond
	redundant? context
	silent     (merge-with-meta context updates)
	true       (merge context (without-memo
				   (notify-watchers context
						    (with-memo updates initial-changes))))))))

(defn touch
  "Runs handlers without modifying a value."
  [context & keys]
  (let [watchers (watchers-for-keys context keys)]
    (run-watchers context context watchers)))

(defn watch-keys
  "Adds a watch that is run only when the key's value changes. f should be a
  function taking two arguments: the old context and the new context."
  [context f & keys]
  (apply alter-watches context multimap/add f keys))

(defn unwatch-keys
  "Removes a watch from the specified keys."
  [context f & keys]
  (apply alter-watches context multimap/del f keys))