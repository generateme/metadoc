(ns metadoc.evaluate-test
  (:require [metadoc.evaluate :refer :all]
            [metadoc.examples :refer :all]
            [clojure.test :refer :all]))

(deftest example-evaluate-test
  (let [ex (evaluate (example "Regular example" (+ 1 2 3)))
        ex-test (evaluate (example "Regular example with test" {:test-value 11} (+ 1 10)))
        ;; ex-test-wrong (evaluate-example (example "Wrong test" {:test-value 0} 1))
        ex-noeval (evaluate (example "Regular example without evaluation" {:evaluate? false} (println "abc")))]
    (is (= (:result ex) ((:example-fn ex))))
    (is (nil? (:result ex-noeval)))
    (is (:test ex-test))))

(deftest example-evaluate-session-test
  (let [ex (evaluate (example-session "Session" (+ 1 2) (+ 3 4) (let [x 11] (* x x))))]
    (is (= [3 7 121] (:result ex)))))

(defsnippet snippet-fn
  "Description"
  (for [x (range 3)
        y (range 3)]
    [(first opts) (f x y)]))

(deftest example-evaluate-snippet-test
  (let [ex (evaluate (example-snippet "Snippet" snippet-fn (fn [x y] (- x y))))]
    (is (= -1 (second (second (:result ex)))))))
