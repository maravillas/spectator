(ns spectator.core
  (:require [spectator.multimap :as multimap])
  (:use	[spectator.map-util]
	[clojure.contrib swing-utils logging]))

(defn- watchers [context]
  (:watchers (meta context)))

(defn with-memo
  "Merges the specified key-value pairs with the context's memo."
  [context memo]
  (with-meta context (merge (meta context) {:memo memo})))

(defn memo
  "Retrieves the memos attached to a context."
  [context]
  (:memo (meta context)))

(defn- merge-changes [context changes]
  (let [new-context (merge context changes)
	new-memo (merge (memo context) (memo changes))]
    (with-memo new-context new-memo)))

(defn- without-memo [context]
  (with-meta context (dissoc (meta context) :memo)))

(defn- run-watchers [old-context new-context watchers]
  (if (first watchers)
    (recur old-context
	   (merge-changes new-context ((first watchers) old-context new-context))
	   (rest watchers))
    new-context))

(defn- watchers-for-keys
  [context keys]
  (let [watchers (watchers context)]
    (distinct (mapcat #(%1 watchers) keys))))

(defn- notify-watchers
  [old-context new-context]
  (let [diff (map-diff old-context new-context)
	watchers (watchers-for-keys new-context diff)
	next-context (run-watchers old-context new-context watchers)]
    (if (seq diff)
      (recur new-context next-context)
      new-context)))

(defn- redundant-update? [context key value]
  (and (contains? context key)
       (= (key context) value)))

(defn update 
  "Updates the context with a new value. If silent is true, watchers are not
notified (defaults to false)."
  ([context key value]
     (update context key value false))

  ([context key value silent]
     (let [new (assoc context key value)
	   initial-changes {:initial-changes {key value}}
	   redundant? (redundant-update? context key value)]
       (cond
	redundant? context
	silent     new
	true       (without-memo (notify-watchers context
						  (with-memo new initial-changes)))))))

(defn touch
  "Runs handlers without modifying a value."
  [context & keys]
  (let [watchers (watchers-for-keys context keys)]
    (run-watchers context context watchers)))

(defn- alter-watches
  [context op f & keys]
  (let [funcs (take (count keys) (repeat f))
	kvs (interleave keys funcs)
	watchers (watchers context)]
    (with-meta context {:watchers (apply op watchers kvs)})))

(defn watch-keys
  "Adds a watch that is run only when the key's value changes. f should be a
function taking two arguments: the old context and the new context."
  [context f & keys]
  (apply alter-watches context multimap/add f keys))

(defn unwatch-keys
  "Removes a watch from the specified keys."
  [context f & keys]
  (apply alter-watches context multimap/del f keys))