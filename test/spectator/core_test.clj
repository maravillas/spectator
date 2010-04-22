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
		(add-updater (fn [old new diff] {:a (max 0 (dec (:b new)))}) :b)
		(add-updater (fn [old new diff] {:b (max 0 (dec (:a new)))}) :a)
		(update {:a 3}))]
    (is (= (:a  map)
	   0))
    (is (= (:b map)
	   0))))

(deftest watches-not-run-when-no-change
  (let [map (-> {:foo 1}
		(add-updater (fn [old new diff] {:ran true}) :foo)
		(update {:foo 1}))]
    (is (not (:ran map)))))

(deftest watches-key
  (let [map (-> {}
		(add-updater (fn [old new diff] {:ran true}) :foo)
		(update {:foo 1}))]
    (is (:ran map))))

(deftest watches-multiple-keys-with-one-watcher
  (let [map (-> {:m 0 :n 0}
		(add-updater (fn [old new diff] {:max (max (:m new) (:n new))}) :m :n)
		(update { :m 1}))]
    (is (= (:max map)
	   1))
    
    (let [map (update map {:n 3})]
      (is (= (:max map)
	     3)))))

(deftest watches-multiple-keys-with-multiple-watchers
  (let [map (-> {}
		(add-updater (fn [old new diff] {:next-i (inc (:i new))}) :i)
		(add-updater (fn [old new diff] {:next-j (dec (:j new))}) :j)
		(update {:i 3})
		(update {:j 8}))]
    (is (= (:next-i map)
	   4))
    (is (= (:next-j map)
	   7))))

(deftest watches-multiple-keys-with-multiple-watchers-in-one-update
  (let [map (-> {}
		(add-updater (fn [old new diff] {:next-i (inc (:i new))}) :i)
		(add-updater (fn [old new diff] {:next-j (dec (:j new))}) :j)
		(update {:i 3 :j 8}))]
    (is (= (:next-i map)
	   4))
    (is (= (:next-j map)
	   7))))

(deftest watches-one-key-with-multiple-watchers
  (let [map (-> {}
		(add-updater (fn [old new diff] {:next-i (inc (:i new))}) :i)
		(add-updater (fn [old new diff] {:prev-i (dec (:i new))}) :i)
		(update {:i 3}))]
    (is (= (:next-i map)
	   4))
    (is (= (:prev-i map)
	   2))))

(deftest gives-latest-watcher-priority
  (let [map (-> {}
		(add-updater (fn [old new diff] {:j (inc (:i new))}) :i)
		(add-updater (fn [old new diff] {:j (dec (:i new))}) :i)
		(update {:i 3}))]
    (is (= (:j map)
	   2))))

(deftest cascades-watcher-chains
  (let [map (-> {}
		(add-updater (fn [old new diff] {:b (inc (:a new))}) :a)
		(add-updater (fn [old new diff] {:c (inc (:b new))}) :b)
		(update {:a 3}))]
    (is (= (:b map)
	   4))
    (is (= (:c map)
	   5))))

(deftest removes-watches
  (let [f (fn [old new diff] {:ran true})
	map (-> {}
		(add-updater f :foo)
		(remove-updater f :foo)
		(update {:foo 1}))]
    (is (not (:ran map)))))

(deftest removes-multiple-watches
  (let [f (fn [old new diff] {:count (inc (:count new))})
	map (-> {:count 0}
		(add-updater f :foo :bar :baz)
		(remove-updater f :foo :bar)
		(update {:foo 1})
		(update {:bar 1})
		(update {:baz 1}))]
    (is (= (:count map)
	   1))))

(deftest runs-watchers-without-changes-for-one-key
  (let [map (-> {:count 0}
		(add-updater (fn [old new diff] {:count (inc (:count new))}) :foo)
		(touch :foo))]
    (is (= (:count map)
	   1))))

(deftest runs-one-watcher-without-changes-for-multiple-keys
  (let [map (-> {:count 0}
		(add-updater (fn [old new diff] {:count (inc (:count new))}) :foo :bar :baz)
		(touch {} nil :foo :bar))]
    (is (= (:count map)
	   1))))

(deftest runs-watchers-without-changes-for-multiple-keys
  (let [map (-> {:count 0}
		(add-updater (fn [old new diff] {:count (inc (:count new))}) :foo :baz)
		(add-updater (fn [old new diff] {:count (inc (:count new))}) :bar)
		(touch {} nil :foo :bar))]
    (is (= (:count map)
	   2))))

(deftest runs-chained-watchers-without-changes
  (let [map (-> {:count 0}
		(add-updater (fn [old new diff] {:bar 1 :count (inc (:count new))}) :foo)
		(add-updater (fn [old new diff] {:count (inc (:count new))}) :bar)
		(touch :foo))]
    (is (= (:count map)
	   2))))

(deftest with-memo-adds-memo
  (is (= (:foo (memo (with-memo {} {:foo 1})))
	 1)))

(deftest updates-memo
  (let [map (-> {}
		(add-updater (fn [old new diff] (with-memo {} {:has-memo true})) :foo)
		(add-updater (fn [old new diff] {:has-memo (:has-memo (memo new))}) :foo)
		(update {:foo 1}))]
    (is (:has-memo map))))

(deftest memo-stripped-after-watches
  (let [map (-> {}
		(add-updater (fn [old new diff] (with-memo {} {:has-memo true})) :foo)
		(update {:foo 1}))]
    (is (not (memo map)))))

(deftest memo-persists-through-watcher-chains
  (let [map (-> {}
		(add-updater (fn [old new diff] (with-memo {:bar 1} {:has-memo true})) :foo)
		(add-updater (fn [old new diff] {:baz 1}) :bar)
		(add-updater (fn [old new diff] {:has-memo (:has-memo (memo new))}) :baz)
		(update {:foo 1}))]
    (is (:has-memo map))))

(deftest includes-initial-changes
  (let [map (-> {}
		(add-updater (fn [old new diff] {:changes (:initial-changes (memo new))}) :foo)
		(update {:foo 7}))]
    (is (= (:changes map)
	   {:foo 7}))))

(deftest watchers-add-to-map
  (let [map (-> {:a 0 :b 1}
		(add-updater (fn [old new diff] {:d 3}) :c)
		(update {:c 2}))]
    (is (= map
	   {:a 0 :b 1 :c 2 :d 3}))))

(deftest updates-silently
  (let [map (-> {}
		(add-updater (fn [old new diff] {:run true}) :foo)
		(update {:foo 1} true))]
    (is (= (:foo map)
	   1))
    (is (not (:run map)))))

(deftest sends-initial-memo
  (let [map (-> {}
		(add-updater (fn [old new diff] {:memo (:foo (memo new))}) :bar)
		(update {:bar 1} false {:foo true}))]
    (is (:memo map))))

(deftest keeps-old-and-new-params-correct
  (let [map (-> {:a 1 :b 2}
		(add-updater (fn [old new diff]
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

(deftest merges-memos-in-updates
  (let [map (-> {}
		(add-updater (fn [old new diff] (with-memo {} {:foo true})) :foo)
		(add-updater (fn [old new diff] (with-memo {} {:bar false})) :foo)
		(add-updater (fn [old new diff] {:memo (memo new)}) :foo)
		(update {:foo 1} false {:foo false}))]
    (is (= (:memo map)
	   {:initial-changes {:foo 1} :foo true :bar false}))))

(deftest vetoes-updates
  (let [map (-> {:foo 1}
		(add-updater (fn [old new diff] {:foo 2}) :bar)
		(add-updater (fn [old new diff] (veto)) :bar)
		(add-updater (fn [old new diff] {:foo 3}) :bar)
		(update {:bar 1}))]
    (is (= (:foo map)
	   1))
    (is (not (:bar map)))))

(deftest vetoes-skip-further-watchers
  (let [map (-> {}
		(add-updater (fn [old new diff] (veto)) :foo)
		(add-updater (fn [old new diff] {:updated true}) :foo)
		(update {:foo 1}))]
    (is (not (:updated map)))))

(deftest preserves-metadata
  (let [map (-> (with-meta {} {:meta true})
		(add-updater (fn [old new diff] {:ran true}) :foo)
		(update {:foo true}))]
    (is (:meta (meta map)))))

(deftest runs-observers
  (let [atom (atom false)
	agent (agent nil)
	map (-> {}
		(add-updater (fn [old new diff] {:pure true}) :foo)
		(add-observer (fn [old new diff] (swap! atom (fn [_] true))) :foo)
		(update {:foo true} false {} agent))]
    (await agent)
    (is @atom)))

(deftest ignores-impure-results
  (let [atom (atom false)
	agent (agent nil)
	map (-> {:ignored true}
		(add-observer (fn [old new diff] {:ignored false}) :foo)
		(update {:foo true} false {} agent))]
    (await agent)
    (is (:ignored map))))

(deftest provides-observers-with-contexts
  (let [atom (atom {})
	agent (agent nil)
	map (-> {:foo false :bar true}
		(add-observer (fn [old new diff] (swap! atom (fn [_] {:old old :new new}))) :foo)
		(update {:foo true} false {} agent))]
    (await agent)
    (is (= (:new @atom)
	   {:foo true :bar true}))
    (is (= (:old @atom)
	   {:foo false :bar true}))))

(deftest runs-observers-for-changed-key-only
  (let [ref1 (ref false)
	ref2 (ref false)
	agent (agent nil)
	map (-> {}
		(add-observer (fn [old new diff] (dosync (ref-set ref1 true))) :foo)
		(add-observer (fn [old new diff] (dosync (ref-set ref2 true))) :bar)
		(update {:foo true} false {} agent))]
    (await agent)
    (is @ref1)
    (is (not @ref2))))

(deftest runs-pure-watchers-before-observers
  (let [ref (ref {})
	agent (agent nil)
	map (-> {:foo false :bar false}
		(add-updater (fn [old new diff] {:bar true}) :foo)
		(add-observer (fn [old new diff] (dosync (ref-set ref new))) :foo)
		(update {:foo true} false {} agent))]
    (await agent)
    (is (= @ref
	   {:foo true :bar true}))))

(deftest runs-observers-without-changes
  (let [ref (ref false)
	agent (agent nil)
	map (-> {}
		(add-observer (fn [old new diff] (dosync (ref-set ref true))) :foo)
		(touch {} agent :foo))]
    (await agent)
    (is @ref)))

(deftest provides-memo-to-observers
  (let [ref (ref {})
	agent (agent nil)
	map (-> {}
		(add-observer (fn [old new diff] (dosync (ref-set ref (memo new)))) :foo)
		(update {:foo true} false {:a true} agent))]
    (await agent)
    (is (= @ref
	   {:initial-changes {:foo true} :a true}))))

(deftest passes-updater-memos-to-observers
  (let [ref (ref {})
	agent (agent nil)
	map (-> {}
		(add-updater (fn [old new diff] (with-memo {} {:updater true})) :foo)
		(add-observer (fn [old new diff] (dosync (ref-set ref (memo new)))) :foo)
		(update {:foo true} false {} agent))]
    (await agent)
    (is (= @ref
	   {:initial-changes {:foo true} :updater true}))))

(deftest provides-memo-to-observers-without-changes
  (let [ref (ref {})
	agent (agent nil)
	map (-> {}
		(add-updater (fn [old new diff] (with-memo {} {:extra-memo true})) :foo)
		(add-observer (fn [old new diff] (dosync (ref-set ref (memo new)))) :foo)
		(touch {} agent :foo))]
    (await agent)
    (is (= @ref
	   {:extra-memo true}))))

(deftest provides-original-memo-to-observers-without-changes
  (let [ref (ref {})
	agent (agent nil)
	map (-> {}
		(add-observer (fn [old new diff] (dosync (ref-set ref (memo new)))) :foo)
		(touch {:bar true} agent :foo))]
    (await agent)
    (is (= @ref
	   {:bar true}))))

(deftest runs-global-updaters
  (let [map (-> {:foo 0 :bar 0 :baz 0}
		(add-updater (fn [old new diff] {:sum (+ (:foo new) (:bar new) (:baz new))}))
		(update {:foo 1})
		(update {:bar 2})
		(update {:baz 3}))]
    (is (= (:sum map)
	   6))))

(deftest removes-global-updaters
  (let [f (fn [old new diff] {:sum (+ (:foo new) (:bar new) (:baz new))})
	map (-> {:foo 0 :bar 0 :baz 0}
		(add-updater f)
		(update {:foo 1})
		(remove-updater f)
		(update {:bar 2})
		(update {:baz 3}))]
    (is (= (:sum map)
	   1))))

(deftest runs-global-observers
  (let [ref (ref 0)
	agent (agent nil)
	map (-> {}
		(add-observer (fn [old new diff] (dosync (commute ref inc))))
		(update {:foo 1} false {} agent)
		(update {:bar 2 :baz 3} false {} agent))]
    (await agent)
    (is (= @ref
	   2))))

(deftest removes-global-observer
  (let [ref (ref 0)
	f (fn [old new diff] (dosync (commute ref inc)))
	agent (agent nil)
	map (-> {}
		(add-observer f)
		(update {:foo 1} false {} agent)
		(remove-observer f)
		(update {:bar 2 :baz 3} false {} agent))]
    (await agent)
    (is (= @ref
	   1))))

(deftest specifies-key-diff-to-updaters
  (let [map (-> {:foo 0 :bar 1 :baz 2}
		(add-updater (fn [old new diff] {:diff1 diff}) :foo)
		(add-updater (fn [old new diff] {:diff2 diff}) :bar)
		(update {:foo 1})
		(update {:bar 2 :baz 3}))]
    (is (= (:diff1 map)
	   [:foo]))
    (is (= (:diff2 map)
	   [:bar :baz]))))

(deftest specifies-key-diff-to-observers
  (let [ref (ref {})
	agent (agent nil)
	map (-> {:foo 0 :bar 1 :baz 2}
		(add-observer (fn [old new diff] (dosync (commute ref merge {:diff1 diff})))
			      :foo)
		(add-observer (fn [old new diff] (dosync (commute ref merge {:diff2 diff})))
			      :bar)
		(update {:foo 1} false {} agent)
		(update {:bar 2 :baz 3} false {} agent))]
    (await agent)
    (is (= (:diff1 @ref)
	   [:foo]))
    (is (= (sort (:diff2 @ref))
	   (sort [:bar :baz])))))

(deftest specifies-only-recent-diff-to-updaters
  (let [map (-> {}
		(add-updater (fn [old new diff] {:b (inc (:a new))}) :a)
		(add-updater (fn [old new diff] {:c (inc (:b new)) :diff diff}) :b)
		(update {:a 3}))]
    (is (= (:diff map)
	   [:b]))))

(deftest specifies-entire-diff-to-observers
  (let [ref (ref {})
	agent (agent nil)
	map (-> {}
		(add-updater (fn [old new diff] {:b (inc (:a new))}) :a)
		(add-updater (fn [old new diff] {:c (inc (:b new))}) :b)
		(add-observer (fn [old new diff] (dosync (commute ref merge {:diff diff})))
			      :b)
		(update {:a 3}))]
    (await agent)
    (is (= (sort (:diff @ref))
	   (sort [:a :b :c])))))