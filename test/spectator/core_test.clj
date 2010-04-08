(ns spectator.core-test
  (:use [spectator.core] :reload-all)
  (:use [clojure.test]))

(deftest updates-key
  (let [map {}
	map (update map {:foo 1})]
    (is (= (:foo map)
	   1))))

(deftest stops-cycles-when-values-are-unchanged
  (let [map {:a 0 :b 3}
	map (watch-keys map (fn [old new] {:a (max 0 (dec (:b new)))}) :b)
	map (watch-keys map (fn [old new] {:b (max 0 (dec (:a new)))}) :a)
	map (update map {:a 3})]
    (is (= (:a  map)
	   0))
    (is (= (:b map)
	   0))))

(deftest watches-not-run-when-no-change
  (let [map {:foo 1}
	map (watch-keys map (fn [old new] {:ran true}) :foo)
	map (update map {:foo 1})]
    (is (not (:ran map)))))

(deftest watches-key
  (let [map {}
	map (watch-keys map (fn [old new] {:ran true}) :foo)
	map (update map {:foo 1})]
    (is (:ran map))))

(deftest watches-multiple-keys-with-one-watcher
  (let [map {:m 0 :n 0}
	map (watch-keys map (fn [old new] {:max (max (:m new) (:n new))}) :m :n)
	map (update map { :m 1})]
    (is (= (:max map)
	   1))
    
    (let [map (update map {:n 3})]
      (is (= (:max map)
	     3)))))

(deftest watches-multiple-keys-with-multiple-watchers
  (let [map {}
	map (watch-keys map (fn [old new] {:next-i (inc (:i new))}) :i)
	map (watch-keys map (fn [old new] {:next-j (dec (:j new))}) :j)
	map (update map {:i 3})
	map (update map {:j 8})]
    (is (= (:next-i map)
	   4))
    (is (= (:next-j map)
	   7))))

(deftest watches-one-key-with-multiple-watchers
  (let [map {}
	map (watch-keys map (fn [old new] {:next-i (inc (:i new))}) :i)
	map (watch-keys map (fn [old new] {:prev-i (dec (:i new))}) :i)
	map (update map {:i 3})]
    (is (= (:next-i map)
	   4))
    (is (= (:prev-i map)
	   2))))

(deftest gives-latest-watcher-priority
  (let [map {}
	map (watch-keys map (fn [old new] {:j (inc (:i new))}) :i)
	map (watch-keys map (fn [old new] {:j (dec (:i new))}) :i)
	map (update map {:i 3})]
    (is (= (:j map)
	   2))))

(deftest cascades-watcher-chains
  (let [map {}
	map (watch-keys map (fn [old new] {:b (inc (:a new))}) :a)
	map (watch-keys map (fn [old new] {:c (inc (:b new))}) :b)
	map (update map {:a 3})]
    (is (= (:b map)
	   4))
    (is (= (:c map)
	   5))))

(deftest removes-watches
  (let [map {}
	f (fn [old new] {:ran true})
	map (watch-keys map f :foo)
	map (unwatch-keys map f :foo)
	map (update map {:foo 1})]
    (is (not (:ran map)))))

(deftest removes-multiple-watches
  (let [map {:count 0}
	f (fn [old new] {:count (inc (:count new))})
	map (watch-keys map f :foo :bar :baz)
	map (unwatch-keys map f :foo :bar)
	map (update map {:foo 1})
	map (update map {:bar 1})
	map (update map {:baz 1})]
    (is (= (:count map)
	   1))))

(deftest runs-watchers-without-changes-for-one-key
  (let [map {:count 0}
	map (watch-keys map (fn [old new] {:count (inc (:count new))}) :foo)
	map (touch map :foo)]
    (is (= (:count map)
	   1))))

(deftest runs-one-watcher-without-changes-for-multiple-keys
  (let [map {:count 0}
	map (watch-keys map (fn [old new] {:count (inc (:count new))}) :foo :bar :baz)
	map (touch map :foo :bar)]
    (is (= (:count map)
	   1))))

(deftest runs-watchers-without-changes-for-multiple-keys
  (let [map {:count 0}
	map (watch-keys map (fn [old new] {:count (inc (:count new))}) :foo :baz)
	map (watch-keys map (fn [old new] {:count (inc (:count new))}) :bar)
	map (touch map :foo :bar)]
    (is (= (:count map)
	   2))))

(deftest with-memo-adds-memo
  (is (= (:foo (memo (with-memo {} {:foo 1})))
	 1)))

(deftest updates-memo
  (let [map {}
	map (watch-keys map (fn [old new] (with-memo {} {:has-memo true})) :foo)
	map (watch-keys map (fn [old new] {:has-memo (:has-memo (memo new))}) :foo)
	map (update map {:foo 1})]
    (is (:has-memo map))))

(deftest memo-stripped-after-watches
  (let [map {}
	map (watch-keys map (fn [old new] (with-memo {} {:has-memo true})) :foo)
	map (update map {:foo 1})]
    (is (not (memo map)))))

(deftest memo-persists-through-watcher-chains
  (let [map {}
	map (watch-keys map (fn [old new] (with-memo {:bar 1} {:has-memo true})) :foo)
	map (watch-keys map (fn [old new] {:baz 1}) :bar)
	map (watch-keys map (fn [old new] {:has-memo (:has-memo (memo new))}) :baz)
	map (update map {:foo 1})]
    (is (:has-memo map))))

(deftest includes-initial-changes
  (let [map {}
	map (watch-keys map (fn [old new] {:changes (:initial-changes (memo new))}) :foo)
	map (update map {:foo 7})]
    (is (= (:changes map)
	   {:foo 7}))))

(deftest watchers-add-to-map
  (let [map {:a 0 :b 1}
	map (watch-keys map (fn [old new] {:d 3}) :c)
	map (update map {:c 2})]
    (is (= map
	   {:a 0 :b 1 :c 2 :d 3}))))

(deftest updates-silently
  (let [map {}
	map (watch-keys map (fn [old new] {:run true}) :foo)
	map (update map {:foo 1} true)]
    (is (= (:foo map)
	   1))
    (is (not (:run map)))))

(deftest sends-initial-memo
  (let [map {}
	map (watch-keys map (fn [old new] {:memo (:foo (memo new))}) :bar)
	map (update map {:bar 1} false {:foo true})]
    (is (:memo map))))

(deftest keeps-old-and-new-params-correct
  (let [map {:a 1 :b 2}
	map (watch-keys map (fn [old new]
			      {:old old :new new}) :c)
	map (update map {:c 3})]
    (is (= (:old map)
	   {:a 1 :b 2}))
    (is (= (:new map)
	   {:a 1 :b 2 :c 3}))))

(deftest merges-memos
  (let [map (with-meta {:foo 1} {:memo {:a 1}})
	map (with-memo map {:b 2})]
    (is (= (memo map)
	   {:a 1 :b 2}))))

(deftest vetoes-updates
  (let [map {:foo 1}
	map (watch-keys map (fn [old new] {:foo 2}) :bar)
	map (watch-keys map (fn [old new] (veto)) :bar)
	map (watch-keys map (fn [old new] {:foo 3}) :bar)
	map (update map {:bar 1})]
    (is (= (:foo map)
	   1))
    (is (not (:bar map)))))

(deftest vetoes-skip-further-watchers
  (let [flag (ref false)
	map {}
	map (watch-keys map (fn [old new] (veto)) :foo)
	map (watch-keys map (fn [old new] (dosync (ref-set flag true))) :foo)
	map (update map {:foo 1})]
    (is (not @flag))))