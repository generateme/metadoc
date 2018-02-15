(ns metadoc.core-test
  (:require [clojure.test :refer :all]
            [metadoc.core :refer :all]))

(deftest example-test
  (let [ex (example "Regular example" (+ 1 2 3))
        ex-md5 (example "Regular example, return md5" md5-hash)
        ex-test (example "Regular example with test" {:test-value 11} (+ 1 10))
        ex-noeval (example "Regular example without evaluation" {:evaluate? false} (println "abc"))]
    (is (= :simple (:type ex)))
    (is (string? (:example ex))) 
    (is (= 6 ((:example-fn ex))))
    (is (nil? (:test-value ex)))
    (is (= (md5 (:example ex-md5)) ((:example-fn ex-md5)))) ;; check if `md5-hash` variable is visible inside example
    (is (= 11 (:test-value ex-test)))
    (is (nil? (:example-fn ex-noeval)))))

(deftest example-session-test
  (let [ex (example-session "Session" (+ 1 2) (+ 3 4) (let [x 11] (* x x)))]
    (is (= :session (:type ex)))
    (is (= 3 (count (:example-fn ex))))
    (is (= 3 (count (:example ex))))
    (is (= 3 ((first (:example-fn ex)))))
    (is (= 7 ((second (:example-fn ex)))))
    (is (= 121 (((:example-fn ex) 2))))))

(defsnippet snippet-fn
  "Description"
  (for [x (range 3)
        y (range 3)]
    [(first opts) (f x y)]))

(deftest example-snippet-test
  (let [ex (example-snippet "Snippet" snippet-fn (fn [x y] (- x y)))]
    (is (= :snippet (:type ex)))
    (is (= -1 (second (second ((:example-fn ex))))))))

(deftest example-url-test
  (let [ex-img (example-image "Image" "a.jpg")
        ex-url (example-url "URL" "http://localhost/")]
    (is (= :image (:type ex-img)))
    (is (= "a.jpg" (:example ex-img)))
    (is (= :url (:type ex-url)))
    (is (string? (:example ex-img)))
    (is (= :anything (:type (example-any-url "Anything" :anything "abc"))))))

(add-examples snippet-fn
  (example "Add something" (* 2 3)))

(deftest add-examples-test
  (is (:examples (meta #'snippet-fn)))
  (is (pos? (count (:examples (meta #'snippet-fn))))))
