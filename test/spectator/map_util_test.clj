(ns spectator.map-util-test
  (:use [spectator.map-util] :reload-all)
  (:use [clojure.test]))

(deftest map-diff-finds-single-diff
  (is (= (map-diff {:a 1 :b 2}
		   {:a 2 :b 2})
	 [:a])))

(deftest map-diff-finds-multiple-diffs
  (is (= (sort (map-diff {:a 1 :b 2 :c 3}
			 {:a 1 :b 9 :c 9}))
	 (sort [:b :c]))))

(deftest map-diff-finds-no-diffs
  (is (= (map-diff {:a 1 :b 2 :c 3}
		   {:a 1 :b 2 :c 3})
	 [])))

(deftest map-diff-finds-new-key
  (is (= (map-diff {:a 1}
		   {:a 1 :b 2})
	 [:b])))

(deftest map-diff-finds-missing-key
  (is (= (map-diff {:a 1 :b 2}
		   {:a 1})
	 [:b])))

(deftest map-diff-allows-empty-maps
  (is (= (map-diff {:a 1}
		   {})
	 [:a]))
  (is (= (map-diff {}
		   {:a 1})
	 [:a])))