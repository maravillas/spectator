(ns spectator.core-test
  (:use [spectator.core] :reload-all)
  (:use [clojure.test]))

(deftest updates-key
  (let [context {}
	context (update context :foo 1)]
    (is (= (:foo context)
	   1))))

(deftest stops-cycles-when-values-are-unchanged
  (let [context {:a 0 :b 3}
	context (watch-keys context (fn [old new] {:a (max 0 (dec (:b new)))}) :b)
	context (watch-keys context (fn [old new] {:b (max 0 (dec (:a new)))}) :a)
	context (update context :a 3)]
    (is (= (:a  context)
	   0))
    (is (= (:b context)
	   0))))

(deftest watches-not-run-when-no-change
  (let [context {:foo 1}
	context (watch-keys context (fn [old new] {:ran true}) :foo)
	context (update context :foo 1)]
    (is (not (:ran context)))))

(deftest watches-key
  (let [context {}
	context (watch-keys context (fn [old new] {:ran true}) :foo)
	context (update context :foo 1)]
    (is (:ran context))))

(deftest watches-multiple-keys-with-one-watcher
  (let [context {:m 0 :n 0}
	context (watch-keys context (fn [old new] {:max (max (:m new) (:n new))}) :m :n)
	context (update context :m 1)]
    (is (= (:max context)
	   1))
    
    (let [context (update context :n 3)]
      (is (= (:max context)
	     3)))))

(deftest watches-multiple-keys-with-multiple-watchers
  (let [context {}
	context (watch-keys context (fn [old new] {:next-i (inc (:i new))}) :i)
	context (watch-keys context (fn [old new] {:next-j (dec (:j new))}) :j)
	context (update context :i 3)
	context (update context :j 8)]
    (is (= (:next-i context)
	   4))
    (is (= (:next-j context)
	   7))))

(deftest watches-one-key-with-multiple-watchers
  (let [context {}
	context (watch-keys context (fn [old new] {:next-i (inc (:i new))}) :i)
	context (watch-keys context (fn [old new] {:prev-i (dec (:i new))}) :i)
	context (update context :i 3)]
    (is (= (:next-i context)
	   4))
    (is (= (:prev-i context)
	   2))))

(deftest gives-latest-watcher-priority
  (let [context {}
	context (watch-keys context (fn [old new] {:j (inc (:i new))}) :i)
	context (watch-keys context (fn [old new] {:j (dec (:i new))}) :i)
	context (update context :i 3)]
    (is (= (:j context)
	   2))))

(deftest cascades-watcher-chains
  (let [context {}
	context (watch-keys context (fn [old new] {:b (inc (:a new))}) :a)
	context (watch-keys context (fn [old new] {:c (inc (:b new))}) :b)
	context (update context :a 3)]
    (is (= (:b context)
	   4))
    (is (= (:c context)
	   5))))
