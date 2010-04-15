(ns spectator.collection-util-test
  (:use [spectator.collection-util] :reload-all)
  (:use [clojure.test]))

(deftest alternate-with-alternates-values
  (is (= (alternate-with [1 2 3 4] 0)
	 [1 0 2 0 3 0 4 0])))

(deftest alternate-with-ignores-empty-collections
  (is (= (alternate-with [] 0)
	 [])))