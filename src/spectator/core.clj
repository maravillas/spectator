(ns spectator.core
  (:require [spectator.multimap :as multimap])
  (:use	[spectator.map-util]
	[clojure.contrib swing-utils logging]))

(defn- run-watchers [old-context context watchers]
  (if (first watchers)
    (recur old-context (merge context ((first watchers) old-context context)) (rest watchers))
    context))

(defn- notify-watchers
  [context updates]
  (let [old-context context
	all-watchers (:watchers (meta context)) 
	watchers (distinct (mapcat #(%1 all-watchers) (keys updates)))
	new-context (run-watchers old-context context watchers)
	diff (map-diff old-context new-context)]
    (if (seq diff)
      (recur new-context (select-keys new-context diff))
      new-context)))

(defn- redundant-update? [context key value]
  (and (contains? context key)
       (= (key context) value)))

(defn update 
  "Updates the context with a new value. If silent is true,
watchers are not notified (defaults to false)."
  ([context key value]
     (update context key value false))

  ([context key value silent]
     (let [new (assoc context key value)]
       (cond
	(redundant-update? context key value) context
	silent new
	true (notify-watchers new {key value})))))

(defn watch-keys
  "Adds a watch to the context that is run only when the key's
value changes. f should be a function taking two arguments: the
old context and the new context."
  [context f & keys]
  (let [kvs (conj (vec (interpose f keys)) f)
	watchers (:watchers (meta context))]
    (with-meta context {:watchers (apply multimap/add watchers kvs)})))