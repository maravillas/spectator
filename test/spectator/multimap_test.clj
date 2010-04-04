(ns spectator.multimap-test
  (:use [spectator.multimap] :reload-all)
  (:use [clojure.test]))

(deftest adds-new-key
  (is (= (add {} :foo 1)
	 {:foo [1]})))

(deftest adds-to-existing-key
  (let [mm (add {} :foo 1)]
    (is (= (add mm :foo 2)
	   {:foo [1 2]}))))

(deftest adds-to-existing-key-in-order
  (let [mm (add {} :foo 100)]
    (is (= (add mm :foo 2)
	   {:foo [100 2]}))))

(deftest adds-multiple-pairs
  (is (= (add {} :foo 1 :bar 2 :baz 7 :quux 1024)
	 {:foo [1] :bar [2] :baz [7] :quux [1024]})))

(deftest adds-multiple-pairs-with-some-existing-keys
  (let [mm (add {} :foo 1 :bar 2)]
    (is (= (add mm :foo 2 :bar 3 :baz 7 :quux 1024)
	   {:foo [1 2] :bar [2 3] :baz [7] :quux [1024]}))))

(deftest redundant-value-not-duplicated
  (let [mm (add {} :foo 1)]
    (is (= (add mm :foo 1)
	   mm))))

(deftest deletes-value-from-multi-valued-key
  (let [mm (add {} :foo 1 :foo 2 :foo 3)]
    (is (= (del mm :foo 2)
	   {:foo [1 3]}))))

(deftest leaves-key-with-empty-value
  (is (= (del {:foo [1]} :foo 1)
	 {:foo []})))

(deftest deletes-multiple-values
  (let [mm {:foo [1 2] :bar [3] :baz [7]}]
    (is (= (del mm :foo 1 :foo 2 :bar 3)
	   {:foo [] :bar [] :baz [7]}))))