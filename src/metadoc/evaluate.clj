(ns metadoc.evaluate
  "This is dumb test doc."
  {:categories {:test-cat "This is cat name!"}}
  (:require [metadoc.examples :refer :all]
            [clojure.test :as test]
            [hiccup.core :refer :all]
            [hiccup.element :refer :all]
            [clojure.string :as s]))

;;

(def ^:const ^:private ^String new-line (System/getProperty "line.separator"))

(defmulti evaluate :type)

(defmethod evaluate :default [ex] (assoc ex :result (:example ex)))

(defn- maybe-format-result
  "Format result for some specific types."
  [res]
  (if (coll? res)
    (format-form res)
    res))

(defn- eval-example-fn
  ""
  [{:keys [example-fn]}]
  (when example-fn
    (if (coll? example-fn)
      (map #(%) example-fn)
      (example-fn))))

(defmethod evaluate :simple [{:keys [example-fn test-value] :as ex}]
  (let [tested (and example-fn test-value)]
    (assoc ex
           :tested tested
           :test (when tested
                   (test/is (= test-value (example-fn))))
           :result (eval-example-fn ex))))

(defmethod evaluate :session [ex]
  (assoc ex :result (eval-example-fn ex)))

(defmethod evaluate :snippet [ex]
  (assoc ex :result (eval-example-fn ex)))

;; format result, double dispatch

(defmulti format-html :type)
(defmethod format-html :default [r] [:div
                                     [:blockquote (:doc r)]
                                     [:pre (:result r)]])

(defn- add-comment
  ""
  [s]
  (->> (str s)
       (s/split-lines)
       (map #(str ";;=> " %))
       (s/join new-line)))

(defn make-code-line
  [example evaluated? result]
  (str example
       (when evaluated?
         (str new-line (add-comment (maybe-format-result result))))))

(defn test-result
  [{:keys [test-value test]}]
  (if test
    ";; Test: ok."
    (str ";; Test: failed, expected value: " test-value ".")))

(defmethod format-html :simple [r]
  (html [:div
         [:blockquote (:doc r)] 
         [:pre [:code {:class "hljs clojure"} (make-code-line (:example r) (:example-fn r) (:result r)) 
                (when (:tested r) [:small new-line new-line (test-result r)])]]]))

(defmethod format-html :snippet [r]
  (format-html (assoc r :type (:dispatch-result r))))

(defmethod format-html :session [r]
  (html [:div
         [:blockquote (:doc r)]
         [:pre [:code {:class "hljs clojure"}
                (s/join new-line (for [[ex res] (map vector (:example r) (or (:result r) (repeatedly (constantly nil))))]
                                   (make-code-line ex (:example-fn r) res)))]]]))

(defmethod format-html :image [r] (html [:div
                                         [:blockquote (:doc r)]
                                         (image (:result r))]))

(defmethod format-html :url [r]  (html [:div
                                        [:blockquote
                                         (link-to (:result r) (:doc r))]]))

(defmulti format-markdown :type)
(defmethod format-markdown :default [r] (:result r))

(defmulti format-example (fn [t & _] t))

(defmethod format-example :html [_ result] (format-html result))
(defmethod format-example :markdown [_ result] (format-markdown result))
(defmethod format-example :default [_ result] (:result result))

;;

(defn extract-constants
  ""
  [ns]
  (->> (ns-publics ns)
       (filter (comp :const meta second))
       (map (fn [[k v]] [k (var-get v)])) 
       (into (sorted-map))))

(defn- proxy? [m]
  (re-find #"proxy\$" (-> m :name str)))

(defn metas-from-public-vars
  ""
  ([ns]
   (->> (ns-publics ns)
        (vals) 
        (map meta)
        (remove proxy?)))
  ([ns k] (filter k (metas-from-public-vars ns))))

(defn extract-categories
  ""
  [ns]
  (->> (metas-from-public-vars ns)
       (reduce (fn [curr m]
                 (reduce #(update-in %1 [%2] conj (:name m))
                         curr
                         (or (:categories m) #{:other}))) (sorted-map))))

(defn extract-examples
  ""
  [ns]
  (->> (metas-from-public-vars ns :examples)
       (map (fn [e] [(:name e) (map evaluate (:examples e))]))
       (into {})))

(defn extract-snippets
  ""
  [ns]
  (:snippets (meta ns)))
