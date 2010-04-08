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

(deftest merge-with-meta-merges
  (let [m1 (with-meta {:a 1} {:meta-a 1})
	m2 (with-meta {:b 2} {:meta-b 2})
	merged (merge-with-meta m1 m2)]
    (is (= merged
	   (merge m1 m2)
	   {:a 1 :b 2}))
    (is (= (meta merged)
	   {:meta-a 1 :meta-b 2}))))

(deftest merge-with-meta-merges-right-to-left
  (let [m1 (with-meta {:a 1 :b 3} {:meta-a 1 :meta-b 3})
	m2 (with-meta {:b 2 :c 3} {:meta-b 2 :meta-c 3})
	merged (merge-with-meta m1 m2)]
    (is (= merged
	   (merge m1 m2)
	   {:a 1 :b 2 :c 3}))
    (is (= (meta merged)
	   {:meta-a 1 :meta-b 2 :meta-c 3}))))

(deftest merge-with-meta-merges-one-map
  (let [m1 (with-meta {:a 1} {:meta-a 1})
	merged (merge-with-meta m1)]
    (is (= merged
	   (merge m1)
	   {:a 1}))
    (is (= (meta merged)
	   {:meta-a 1}))))

(deftest merge-with-meta-merges-no-maps
  (is (= (merge-with-meta)
	 (merge))))

(deftest map-subset?-identifies-empty-set-as-subset
  (is (map-subset? {} {:a 1})))

(deftest map-subset?-identifies-subset
  (let [m1 {:a 1 :b 2}
	m2 {:a 1 :b 2 :d 7}]
    (is (map-subset? m1 m2))))

(deftest map-subset?-differentiates-on-values
  (let [m1 {:a 1}
	m2 {:a 2 :b 2}]
    (is (not (map-subset? m1 m2)))))

(deftest map-subset?-differentiates-on-keys
  (let [m1 {:a 1}
	m2 {:b 1}]
    (is (not (map-subset? m1 m2)))))
