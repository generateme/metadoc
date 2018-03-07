(ns metadoc.reader
  "Read metadata from given namespace.

  Return information extracted from variables' metadata. Currently supported:

  * constants
  * examples and snippets
  * categories"
  (:require [metadoc.examples :refer :all]))

(defn extract-constants
  "Extract all constants for given namespace `ns`.

  Returns sorted map with symbol as a key and constant value."
  [ns]
  (->> (ns-publics ns)
       (filter (comp :const meta second))
       (map (fn [[k v]] [k (var-get v)])) 
       (into (sorted-map))))

(defn- proxy? [m]
  (re-find #"proxy\$" (-> m :name str)))

(defn- protocol? [var]
  (let [value (var-get var)]
    (and (map? value)
         (not (sorted? value)) ; workaround for CLJ-1242
         (:on-interface value))))

(defn metas-from-public-vars
  "Returns metadata for given `ns`.

  When key (or pred) `k` is provided, filter result and return only metadata containing given key/pred."
  ([ns]
   (->> (ns-publics ns)
        (vals)
        (remove protocol?)
        (map meta)
        (remove proxy?)))
  ([ns k] (filter k (metas-from-public-vars ns))))

(defn extract-categories
  "Returns categories and all symbols for each category."
  [ns]
  (->> (metas-from-public-vars ns)
       (remove :const)
       (reduce (fn [curr m]
                 (reduce #(update-in %1 [%2] conj (:name m))
                         curr
                         (or (:metadoc/categories m) #{:other}))) (sorted-map))))

(defn extract-examples
  "Returns all examples for each var. Examples are evaluated."
  [ns]
  (->> (metas-from-public-vars ns :metadoc/examples)
       (map (fn [e] [(:name e) (map evaluate (:metadoc/examples e))]))
       (into {})))

(defn extract-snippets
  "Returns all snippets for given namespace `ns`."
  [ns]
  (:metadoc/snippets (meta ns)))

