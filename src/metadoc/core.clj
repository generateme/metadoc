(ns metadoc.core
  (:require [zprint.core :refer [zprint-str]]
            [clojure.string :as s])
  (:import [java.security MessageDigest]
           [java.math BigInteger]))

;;
(def ^:const default-format-width 72)
(def ^:dynamic *format-width* default-format-width)

;;

(defn- format-form
  ""
  ([f w]
   (zprint-str f w {:style :community}))
  ([f] (format-form f *format-width*)))

;;

(defn meta-append-to-vector
  "For var `vr` append value(s) `v` to vector under the key `k`."
  [vr k & v]
  (alter-meta! vr update k (fnil concat []) v))

(defn meta-add-to-key
  "For var `vr` append value `v` to the map under the key `k`."
  [vr name k v]
  (alter-meta! vr update-in [k] assoc name v))

;; examples

(def ^:private ^MessageDigest md5-digest (MessageDigest/getInstance "MD5"))

(defn md5
  "Return `md5` hash for given String `s`."
  [^String s]
  (format "%032x" (BigInteger. (int 1) (.digest md5-digest (.getBytes s)))))

(defn- build-fn-with-hash
  ""
  [hash form]
  (let [md5-sym (symbol "md5-hash")]
    `(let [~md5-sym ~hash]
       (fn [] ~form))))

(defmacro example
  ""
  {:style/indent :defn}
  ([description {:keys [evaluate? test-value]
                 :or {evaluate? true test-value nil}} example]
   (let [as-str (format-form example)
         as-fn (when evaluate? (build-fn-with-hash (md5 as-str) example))]
     `{:type :simple
       :doc ~description
       :example ~as-str
       :test-value ~test-value
       :example-fn ~as-fn}))
  ([description example] `(example ~description {} ~example)))

(defmacro example-session
  ""
  {:style/indent :defn}
  [description & examples]
  (let [[evaluate? examples] (if (first examples) [true examples] [false (next examples)])
        as-strs (mapv format-form examples)
        as-fns (when evaluate? (mapv #(build-fn-with-hash (md5 %1) %2) as-strs examples))] 
    `{:type :session
      :doc ~description
      :example ~as-strs
      :example-fn ~as-fns}))

(defmacro defsnippet
  ""
  {:style/indent :defn}
  [name description snippet]
  (let [mname (vary-meta name assoc :private true)
        fun (list 'defn mname '[f & opts] snippet)
        as-str (format-form fun)]
    (meta-add-to-key *ns* name :snippets {:doc description 
                                          :fn-str as-str})
    fun))

(defmacro example-snippet
  ""
  {:style/indent :defn}
  ([description snippet-name dispatch-result example]
   (let [sname (str snippet-name)
         as-str (format-form (list snippet-name example (symbol "...")))]
     `{:type :snippet
       :doc ~description
       :example ~as-str
       :snippet-name ~sname
       :dispatch-result ~dispatch-result
       :example-fn (partial ~snippet-name ~example ~(md5 as-str))}))
  ([description snippet-name example]
   `(example-snippet ~description ~snippet-name :simple ~example)))

(defmacro example-any-url
  ""
  {:style/indent :defn}
  [description typ url]
  `{:type ~typ
    :doc ~description
    :example ~url})

(defmacro example-image
  ""
  {:style/indent :defn}
  [description image-url]
  `(example-any-url ~description :image ~image-url))

(defmacro example-url
  ""
  {:style/indent :defn}
  [description url]
  `(example-any-url ~description :url ~url))

(defmacro add-examples
  ""
  {:style/indent :defn}
  [v & examples]
  `(meta-append-to-vector (var ~v) :examples ~@examples))
