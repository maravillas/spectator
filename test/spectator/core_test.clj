(ns spectator.core-test
  (:use [spectator.core] :reload-all)
  (:use [clojure.test]))

(deftest updates-key
  (let [map (-> {}
		(update {:foo 1}))]
    (is (= (:foo map)
	   1))))

(deftest updates-multiple-keys
  (let [map (-> {}
		(update {:foo 1 :bar 2}))]
    (is (= (:foo map)
	   1))
    (is (= (:bar map)
	   2))))

(deftest stops-cycles-when-values-are-unchanged
  (let [map (-> {:a 0 :b 3}
		(watch-keys (fn [old new] {:a (max 0 (dec (:b new)))}) :b)
		(watch-keys (fn [old new] {:b (max 0 (dec (:a new)))}) :a)
		(update {:a 3}))]
    (is (= (:a  map)
	   0))
    (is (= (:b map)
	   0))))

(deftest watches-not-run-when-no-change
  (let [map (-> {:foo 1}
		(watch-keys (fn [old new] {:ran true}) :foo)
		(update {:foo 1}))]
    (is (not (:ran map)))))

(deftest watches-key
  (let [map (-> {}
		(watch-keys (fn [old new] {:ran true}) :foo)
		(update {:foo 1}))]
    (is (:ran map))))

(deftest watches-multiple-keys-with-one-watcher
  (let [map (-> {:m 0 :n 0}
		(watch-keys (fn [old new] {:max (max (:m new) (:n new))}) :m :n)
		(update { :m 1}))]
    (is (= (:max map)
	   1))
    
    (let [map (update map {:n 3})]
      (is (= (:max map)
	     3)))))

(deftest watches-multiple-keys-with-multiple-watchers
  (let [map (-> {}
		(watch-keys (fn [old new] {:next-i (inc (:i new))}) :i)
		(watch-keys (fn [old new] {:next-j (dec (:j new))}) :j)
		(update {:i 3})
		(update {:j 8}))]
    (is (= (:next-i map)
	   4))
    (is (= (:next-j map)
	   7))))

(deftest watches-multiple-keys-with-multiple-watchers-in-one-update
  (let [map (-> {}
		(watch-keys (fn [old new] {:next-i (inc (:i new))}) :i)
		(watch-keys (fn [old new] {:next-j (dec (:j new))}) :j)
		(update {:i 3 :j 8}))]
    (is (= (:next-i map)
	   4))
    (is (= (:next-j map)
	   7))))

(deftest watches-one-key-with-multiple-watchers
  (let [map (-> {}
		(watch-keys (fn [old new] {:next-i (inc (:i new))}) :i)
		(watch-keys (fn [old new] {:prev-i (dec (:i new))}) :i)
		(update {:i 3}))]
    (is (= (:next-i map)
	   4))
    (is (= (:prev-i map)
	   2))))

(deftest gives-latest-watcher-priority
  (let [map (-> {}
		(watch-keys (fn [old new] {:j (inc (:i new))}) :i)
		(watch-keys (fn [old new] {:j (dec (:i new))}) :i)
		(update {:i 3}))]
    (is (= (:j map)
	   2))))

(deftest cascades-watcher-chains
  (let [map (-> {}
		(watch-keys (fn [old new] {:b (inc (:a new))}) :a)
		(watch-keys (fn [old new] {:c (inc (:b new))}) :b)
		(update {:a 3}))]
    (is (= (:b map)
	   4))
    (is (= (:c map)
	   5))))

(deftest removes-watches
  (let [f (fn [old new] {:ran true})
	map (-> {}
		(watch-keys f :foo)
		(unwatch-keys f :foo)
		(update {:foo 1}))]
    (is (not (:ran map)))))

(deftest removes-multiple-watches
  (let [f (fn [old new] {:count (inc (:count new))})
	map (-> {:count 0}
		(watch-keys f :foo :bar :baz)
		(unwatch-keys f :foo :bar)
		(update {:foo 1})
		(update {:bar 1})
		(update {:baz 1}))]
    (is (= (:count map)
	   1))))

(deftest runs-watchers-without-changes-for-one-key
  (let [map (-> {:count 0}
		(watch-keys (fn [old new] {:count (inc (:count new))}) :foo)
		(touch :foo))]
    (is (= (:count map)
	   1))))

(deftest runs-one-watcher-without-changes-for-multiple-keys
  (let [map (-> {:count 0}
		(watch-keys (fn [old new] {:count (inc (:count new))}) :foo :bar :baz)
		(touch nil :foo :bar))]
    (is (= (:count map)
	   1))))

(deftest runs-watchers-without-changes-for-multiple-keys
  (let [map (-> {:count 0}
		(watch-keys (fn [old new] {:count (inc (:count new))}) :foo :baz)
		(watch-keys (fn [old new] {:count (inc (:count new))}) :bar)
		(touch nil :foo :bar))]
    (is (= (:count map)
	   2))))

(deftest runs-chained-watchers-without-changes
  (let [map (-> {:count 0}
		(watch-keys (fn [old new] {:bar 1 :count (inc (:count new))}) :foo)
		(watch-keys (fn [old new] {:count (inc (:count new))}) :bar)
		(touch :foo))]
    (is (= (:count map)
	   2))))

(deftest with-memo-adds-memo
  (is (= (:foo (memo (with-memo {} {:foo 1})))
	 1)))

(deftest updates-memo
  (let [map (-> {}
		(watch-keys (fn [old new] (with-memo {} {:has-memo true})) :foo)
		(watch-keys (fn [old new] {:has-memo (:has-memo (memo new))}) :foo)
		(update {:foo 1}))]
    (is (:has-memo map))))

(deftest memo-stripped-after-watches
  (let [map (-> {}
		(watch-keys (fn [old new] (with-memo {} {:has-memo true})) :foo)
		(update {:foo 1}))]
    (is (not (memo map)))))

(deftest memo-persists-through-watcher-chains
  (let [map (-> {}
		(watch-keys (fn [old new] (with-memo {:bar 1} {:has-memo true})) :foo)
		(watch-keys (fn [old new] {:baz 1}) :bar)
		(watch-keys (fn [old new] {:has-memo (:has-memo (memo new))}) :baz)
		(update {:foo 1}))]
    (is (:has-memo map))))

(deftest includes-initial-changes
  (let [map (-> {}
		(watch-keys (fn [old new] {:changes (:initial-changes (memo new))}) :foo)
		(update {:foo 7}))]
    (is (= (:changes map)
	   {:foo 7}))))

(deftest watchers-add-to-map
  (let [map (-> {:a 0 :b 1}
		(watch-keys (fn [old new] {:d 3}) :c)
		(update {:c 2}))]
    (is (= map
	   {:a 0 :b 1 :c 2 :d 3}))))

(deftest updates-silently
  (let [map (-> {}
		(watch-keys (fn [old new] {:run true}) :foo)
		(update {:foo 1} true))]
    (is (= (:foo map)
	   1))
    (is (not (:run map)))))

(deftest sends-initial-memo
  (let [map (-> {}
		(watch-keys (fn [old new] {:memo (:foo (memo new))}) :bar)
		(update {:bar 1} false {:foo true}))]
    (is (:memo map))))

(deftest keeps-old-and-new-params-correct
  (let [map (-> {:a 1 :b 2}
		(watch-keys (fn [old new]
			      {:old old :new new}) :c)
		(update {:c 3}))]
    (is (= (:old map)
	   {:a 1 :b 2}))
    (is (= (:new map)
	   {:a 1 :b 2 :c 3}))))

(deftest merges-memos
  (let [map (-> (with-meta {:foo 1} {:memo {:a 1}})
		(with-memo {:b 2}))]
    (is (= (memo map)
	   {:a 1 :b 2}))))

(deftest vetoes-updates
  (let [map (-> {:foo 1}
		(watch-keys (fn [old new] {:foo 2}) :bar)
		(watch-keys (fn [old new] (veto)) :bar)
		(watch-keys (fn [old new] {:foo 3}) :bar)
		(update {:bar 1}))]
    (is (= (:foo map)
	   1))
    (is (not (:bar map)))))

(deftest vetoes-skip-further-watchers
  (let [flag (ref false)
	map (-> {}
		(watch-keys (fn [old new] (veto)) :foo)
		(watch-keys (fn [old new] (dosync (ref-set flag true))) :foo)
		(update {:foo 1}))]
    (is (not @flag))))

(deftest preserves-metadata
  (let [map (-> (with-meta {} {:meta true})
		(watch-keys (fn [old new] {:ran true}) :foo)
		(update {:foo true}))]
    (is (:meta (meta map)))))

(deftest runs-impure-watchers
  (let [atom (atom false)
	agent (agent nil)
	map (-> {}
		(watch-keys (fn [old new] {:pure true}) :foo)
		(watch-keys-impure (fn [old new] (swap! atom (fn [_] true))) :foo)
		(update {:foo true} false {} agent))]
    (await agent)
    (is @atom)))

(deftest ignores-impure-results
  (let [atom (atom false)
	agent (agent nil)
	map (-> {:ignored true}
		(watch-keys-impure (fn [old new] {:ignored false}) :foo)
		(update {:foo true} false {} agent))]
    (await agent)
    (is (:ignored map))))

(deftest provides-impure-watchers-with-contexts
  (let [atom (atom {})
	agent (agent nil)
	map (-> {:foo false :bar true}
		(watch-keys-impure (fn [old new] (swap! atom (fn [_] {:old old :new new}))) :foo)
		(update {:foo true} false {} agent))]
    (await agent)
    (is (= (:new @atom)
	   {:foo true :bar true}))
    (is (= (:old @atom)
	   {:foo false :bar true}))))

(deftest runs-impure-watchers-for-changed-key-only
  (let [ref1 (ref false)
	ref2 (ref false)
	agent (agent nil)
	map (-> {}
		(watch-keys-impure (fn [old new] (dosync (ref-set ref1 true))) :foo)
		(watch-keys-impure (fn [old new] (dosync (ref-set ref2 true))) :bar)
		(update {:foo true} false {} agent))]
    (await agent)
    (is @ref1)
    (is (not @ref2))))

(deftest runs-pure-watchers-before-impure-watchers
  (let [ref (ref {})
	agent (agent nil)
	map (-> {:foo false :bar false}
		(watch-keys (fn [old new] {:bar true}) :foo)
		(watch-keys-impure (fn [old new] (dosync (ref-set ref new))) :foo)
		(update {:foo true} false {} agent))]
    (await agent)
    (is (= @ref
	   {:foo true :bar true}))))

(deftest runs-impure-watchers-without-changes
  (let [ref (ref false)
	agent (agent nil)
	map (-> {}
		(watch-keys-impure (fn [old new] (dosync (ref-set ref true))) :foo)
		(touch agent :foo))]
    (await agent)
    (is @ref)))