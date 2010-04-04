(ns spectator.core
  (:require [spectator.multimap :as multimap])
  (:use	[spectator.map-util]
	[clojure.contrib swing-utils logging]))

(defn- run-watchers [old-context new-context watchers]
  (if (first watchers)
    (recur old-context
	   (merge new-context ((first watchers) old-context new-context))
	   (rest watchers))
    new-context))

(defn- notify-watchers
  [old-context new-context]
  (let [all-watchers (:watchers (meta new-context))
	diff (map-diff old-context new-context)
	watchers (distinct (mapcat #(%1 all-watchers) diff))
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
     (let [new (assoc context key value)]
       (cond
	(redundant-update? context key value) context
	silent new
	true (notify-watchers context new)))))

(defn- alter-watches
  [context op f & keys]
  (let [kvs (conj (vec (interpose f keys)) f)
	watchers (:watchers (meta context))]
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
