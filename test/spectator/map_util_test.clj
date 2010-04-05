(ns spectator.map-util-test
  (:use [spectator.map-util] :reload-all)
  (:use [clojure.test]))

(deftest map-diff-finds-single-diff
  (is (= (map-diff {:a 1 :b 2}
		   {:a 2 :b 2})
	 {:a 2})))

(deftest map-diff-finds-multiple-diffs
  (is (= (map-diff {:a 1 :b 2 :c 3}
		   {:a 1 :b 9 :c 9})
	 {:b 9 :c 9})))

(deftest map-diff-finds-no-diffs
  (is (= (map-diff {:a 1 :b 2 :c 3}
		   {:a 1 :b 2 :c 3})
	 {})))

(deftest map-diff-finds-new-key
  (is (= (map-diff {:a 1}
		   {:a 1 :b 2})
	 {:b 2})))

(deftest map-diff-finds-missing-key
  (is (= (map-diff {:a 1 :b 2}
		   {:a 1})
	 {:b nil})))

(deftest map-diff-allows-empty-maps
  (is (= (map-diff {:a 1}
		   {})
	 {:a nil}))
  (is (= (map-diff {}
		   {:a 1})
	 {:a 1})))