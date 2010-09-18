(ns spectator.core-observers-test
  (:use [spectator.core] :reload-all)
  (:use [clojure.test]))

(deftest runs-observers-when-touching-multiple-keys
  (let [ref (ref 0)
	agent (agent nil)
	map (-> {:foo 0 :bar 0}
		(add-observer (fn [old new diff] (dosync (commute ref inc))) :foo :baz)
		(add-observer (fn [old new diff] (dosync (commute ref inc))) :bar)
		(touch [:foo :bar] :agent agent))]
    (await agent)
    (is (= @ref
	   2))))

(deftest runs-observers
  (let [atom (atom false)
	agent (agent nil)
	map (-> {}
		(add-updater (fn [old new diff] {:pure true}) :foo)
		(add-observer (fn [old new diff] (swap! atom (fn [_] true))) :foo)
		(update {:foo true} :agent agent))]
    (await agent)
    (is @atom)))

(deftest ignores-impure-results
  (let [atom (atom false)
	agent (agent nil)
	map (-> {:ignored true}
		(add-observer (fn [old new diff] {:ignored false}) :foo)
		(update {:foo true} :agent agent))]
    (await agent)
    (is (:ignored map))))

(deftest provides-observers-with-contexts
  (let [atom (atom {})
	agent (agent nil)
	map (-> {:foo false :bar true}
		(add-observer (fn [old new diff] (swap! atom (fn [_] {:old old :new new}))) :foo)
		(update {:foo true} :agent agent))]
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
		(update {:foo true} :agent agent))]
    (await agent)
    (is @ref1)
    (is (not @ref2))))

(deftest runs-pure-watchers-before-observers
  (let [ref (ref {})
	agent (agent nil)
	map (-> {:foo false :bar false}
		(add-updater (fn [old new diff] {:bar true}) :foo)
		(add-observer (fn [old new diff] (dosync (ref-set ref new))) :foo)
		(update {:foo true} :agent agent))]
    (await agent)
    (is (= @ref
	   {:foo true :bar true}))))

(deftest runs-observers-when-touching
  (let [ref (ref false)
	agent (agent nil)
	map (-> {}
		(add-observer (fn [old new diff] (dosync (ref-set ref true))) :foo)
		(touch :foo :agent agent))]
    (await agent)
    (is @ref)))

(deftest provides-memo-to-observers
  (let [ref (ref {})
	agent (agent nil)
	map (-> {}
		(add-observer (fn [old new diff] (dosync (ref-set ref (memo new)))) :foo)
		(update {:foo true} :memo {:a true} :agent agent))]
    (await agent)
    (is (= @ref
	   {:initial-changes {:foo true} :a true}))))

(deftest passes-updater-memos-to-observers
  (let [ref (ref {})
	agent (agent nil)
	map (-> {}
		(add-updater (fn [old new diff] (with-memo {} {:updater true})) :foo)
		(add-observer (fn [old new diff] (dosync (ref-set ref (memo new)))) :foo)
		(update {:foo true} :agent agent))]
    (await agent)
    (is (= @ref
	   {:initial-changes {:foo true} :updater true}))))

(deftest provides-memo-to-observers-without-changes
  (let [ref (ref {})
	agent (agent nil)
	map (-> {}
		(add-updater (fn [old new diff] (with-memo {} {:extra-memo true})) :foo)
		(add-observer (fn [old new diff] (dosync (ref-set ref (memo new)))) :foo)
		(touch :foo :agent agent))]
    (await agent)
    (is (= @ref
	   {:extra-memo true}))))

(deftest provides-original-memo-to-observers-without-changes
  (let [ref (ref {})
	agent (agent nil)
	map (-> {}
		(add-observer (fn [old new diff] (dosync (ref-set ref (memo new)))) :foo)
		(touch :foo :memo {:bar true} :agent agent))]
    (await agent)
    (is (= @ref
	   {:bar true}))))

(deftest runs-global-observers
  (let [ref (ref 0)
	agent (agent nil)
	map (-> {}
		(add-observer (fn [old new diff] (dosync (commute ref inc))))
		(update {:foo 1} :agent agent)
		(update {:bar 2 :baz 3} :agent agent))]
    (await agent)
    (is (= @ref
	   2))))

(deftest removes-global-observer
  (let [ref (ref 0)
	f (fn [old new diff] (dosync (commute ref inc)))
	agent (agent nil)
	map (-> {}
		(add-observer f)
		(update {:foo 1} :agent agent)
		(remove-observer f)
		(update {:bar 2 :baz 3} :agent agent))]
    (await agent)
    (is (= @ref
	   1))))

(deftest specifies-key-diff-to-observers
  (let [ref (ref {})
	agent (agent nil)
	map (-> {:foo 0 :bar 1 :baz 2}
		(add-observer (fn [old new diff] (dosync (commute ref merge {:diff1 diff})))
			      :foo)
		(add-observer (fn [old new diff] (dosync (commute ref merge {:diff2 diff})))
			      :bar)
		(update {:foo 1} :agent agent)
		(update {:bar 2 :baz 3} :agent agent))]
    (await agent)
    (is (= (:diff1 @ref)
	   [:foo]))
    (is (= (sort (:diff2 @ref))
	   (sort [:bar :baz])))))

(deftest specifies-entire-diff-to-observers
  (let [ref (ref {})
	agent (agent nil)
	map (-> {}
		(add-updater (fn [old new diff] {:b (inc (:a new))}) :a)
		(add-updater (fn [old new diff] {:c (inc (:b new))}) :b)
		(add-observer (fn [old new diff] (dosync (commute ref merge {:diff diff})))
			      :b)
		(update {:a 3} :agent agent))]
    (await agent)
    (is (= (sort (:diff @ref))
	   (sort [:a :b :c])))))