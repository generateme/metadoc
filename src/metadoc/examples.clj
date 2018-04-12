(ns metadoc.examples
  "Attach unit examples to your vars.

  Unit examples are small executable code snippets, images, urls or any data which can illustrate usage of your functions.
  Term \"unit\" means that example should be connected directly to given function.

  ### Usage
  
  There are several types of examples (macro - type - description):

  * [[example]] - `:simple` - simple executable code, can be converted to test
  * [[example-session]] - `:session` - list of executable code lines
  * [[example-image]] - `:image` - image url
  * [[example-url]] - `:url` - just any url
  * [[example-snippet]] - `:snippet` - define function which is passed to snippet. Result of snippet call is result of such example. Result can be interpreted as other example type. See below for concrete snippet call. Snippets are created with [[defsnippet]] macro.

  Example is just a map with following fields:

  * `:type` - type of example
  * `:example` - formatted code(s) as a text or just text (for [[example-url]] and [[example-image]])
  * `:example-fn` - code(s) as functions (code is evaluated during documentation creation phase)
  * `:test-value` - if you want to convert your `:simple` example to test this field keeps expected value (test is run during evaluation phase)
  * `:snippet-name` - only for `:snippet` type, name of the snippet function created with [[defsnippet]] macro.
  * `:dispatch-result` - only for `:snippet` type, how to treat result of example evaluation

  NOTE: To add examples to your namespace you have to set [[*process-examples*]] dynamic variable to true.
  
  #### Adding examples

  You can add examples directly to your metadata under `:examples` tag as a list of `(example...)` macro calls.
  
  ```
  (defn some-function
    {:metadoc/examples [(example \"Simple\" (+ 1 2 3))]}
    [])
  ```

  Or call [[add-examples]] macro, pass variable name and examples.

  ```
  (add-examples some-function
    (example \"Another example\" (some-function 1 2 3)))
  ```
  
  #### Snippets

  Sometimes you want to show your example as simple function which should be evaluated by other, more complicated code. Eg. you want to generate some math function plots, calculate something or process data. And you want to reuse such code several times in you example. 

  ```
  (defsnippet my-snippet \"Description\" (f 1 2))

  (example-snippet \"Use snippet to sum something\" my-snippet +) ;;=> 3
  (example-snippet \"Use snippet to multiply somenting\" my-snippet *) ;;=> 2
  ```

  [[defsnippet]] creates private function which accepts function (code from your example will be passed) and `opts` list which currently contains one element, `md5-hash` of example code.
  
  #### Details

  * Each example code is wrapped in the function which has an access to `md5-hash` value. It's a hash of formatted example string.
  * Snippet can be marked as `:hidden` if you don't want to show it in documentation.
  * Result of snippet can be changed to other example type, this way you can easily convert result to image or url.

  ### Evaluation

  Evaluate given example based on type.

  [[evaluate]] function is multimethod with dispatch on example type. When you create your own example macro you need also create corresponding evaluation function.
  
  ### Rendering

  Render to given format.

  Call multimethod [[format-example]] with format type as a dispatch. Currently supported are:

  * `:html`
  * `:markdown` (not implemented yet)
  * `:text` (not implemented yet)

  Each formatting function is also multimethod `format-...` with dispatch on example type.

  Again, if you want to write different formatter - just add corresponding multimethods."
  
  {:metadoc/categories {:meta "Metadata manipulations"
                        :example "Example macros"
                        :format "Format functions"
                        :eval "Evaluation"}}
  (:require [zprint.core :refer [zprint-str]]
            [clojure.test :as test]
            [hiccup.core :refer :all]
            [hiccup.element :refer :all]
            [clojure.string :as s])
  (:import [java.security MessageDigest]
           [java.math BigInteger]))

(def ^:const ^{:doc "Maximum width of formatted code."} default-format-width 72)
(def ^:dynamic ^{:doc "Set maximum width of formatted code. Default 72."} *format-width* default-format-width)

(def ^:dynamic ^{:doc "Enable processing of examples. Default: `false`"} *process-examples* false)

(defn format-form
  "Format your code with `zprint` library.

  Provide form `f` and format it for given width `w` (optional, default [[*format-width*]]."
  {:metadoc/categories #{:format}}
  ([f w]
   (zprint-str f w {:style :community}))
  ([f] (format-form f *format-width*)))

;;

(defn meta-append-to-vector 
  "For var `vr` append value(s) `v` to vector under the key `k`."
  {:metadoc/categories #{:meta}}
  [vr k & v]
  (alter-meta! vr update k (fnil concat []) v))

(defn meta-add-to-key
  "For var `vr` append value `v` to the map under the key `k`."
  {:metadoc/categories #{:meta}}
  [vr name k v]
  (alter-meta! vr update-in [k] assoc name v))

;; examples

(def ^:private ^MessageDigest md5-digest (MessageDigest/getInstance "MD5"))

(defn md5
  "Return `md5` hash for given String `s`."
  [^String s]
  (format "%032x" (BigInteger. (int 1) (.digest md5-digest (.getBytes s)))))

(defn- build-fn-with-hash
  "Create lambda from example code and assign `md5-hash` value."
  [hash form]
  (let [md5-sym (symbol "md5-hash")]
    `(let [~md5-sym ~hash]
       (fn [] ~form))))

(defmacro add-examples
  "Add list of examples to given var.

  Usage:

  ```
  (add-examples any-var
    (example \"one\" form-1)
    (example \"two\" form-2)
    (example-image \"image\" \"aaa.jpg\"))
  ```"
  {:style/indent :defn
   :metadoc/categories #{:example :meta}}
  [v & examples]
  `(when *process-examples* 
     (meta-append-to-vector (var ~v) :metadoc/examples ~@examples)))

(defmacro example
  "Create `:simple` example.
  
  Optional parameters:

  * `:evaluate?` - evaluate code or not (default: `true`)
  * `:test-value` - run test if `:test-value` is not nil (default: `nil`)

  Your code has an access to `md5-hash` value, which is unique String for each form."
  {:style/indent :defn
   :metadoc/categories #{:example}}
  ([description {:keys [evaluate? test-value]
                 :or {evaluate? true test-value nil}} example]
   (let [as-str (format-form example)
         md5-hash (md5 as-str)] 
     `(when *process-examples*
        {:type :simple
         :doc ~description
         :example ~as-str
         :test-value ~test-value
         :example-fn ~(when evaluate? (build-fn-with-hash md5-hash example))})))
  ([description example] `(example ~description {} ~example)))

(defmacro example-session
  "Create `:session` example as a list of code lines. Forms will be evaluated one by one.

  When you pass `false` or `nil` as a second argument, code won't be evaluated.

  Every form has an access to its md5 hash."
  {:style/indent :defn
   :metadoc/categories #{:example}} 
  [description & examples]
  (let [[evaluate? examples] (if (first examples) [true examples] [false (next examples)])
        as-strs (mapv format-form examples)] 
    `(when *process-examples*
       {:type :session
        :doc ~description 
        :example ~as-strs
        :example-fn ~(when evaluate? (mapv #(build-fn-with-hash (md5 %1) %2) as-strs examples))})))

(defmacro defsnippet
  "Create snippet function.
  Snippet is used as a function which is called during evaluation of `example-snippet` code. Example code is passed to snippet.

  Result from snippet can be treated as result of any example type (`:simple` as default).

  When you set `hidden?` parameter to true. Doc generation tool should skip it."
  {:style/indent :defn
   :metadoc/categories #{:example}}
  ([name description hidden? snippet]
   (let [mname (vary-meta name assoc
                          :private true
                          :hidden hidden?)
         fun (list 'defn mname '[f & opts] snippet)
         as-str (format-form fun)]
     (meta-add-to-key *ns* (str name) :metadoc/snippets {:doc description 
                                                         :fn-str as-str})
     fun))
  ([name description snippet]
   `(defsnippet ~name ~description false ~snippet)))

(defmacro example-snippet
  "Define function which will be passed to snippet. Convert result to any example type (default `:simple`).

  Parameters:

  * `snippet-name` - name of the snippet used.
  * `dispatch-result` - treat result as result from different example type (optional, default `:simple`).
  * `example` - function passed to the snippet during evaluation."
  {:style/indent :defn
   :metadoc/categories #{:example}}
  ([description snippet-name dispatch-result example]
   (let [sname (str snippet-name)
         as-str (format-form (list snippet-name example (symbol "...")))]
     `(when *process-examples*
        {:type :snippet
         :doc ~description
         :example ~as-str
         :snippet-name ~sname
         :dispatch-result ~dispatch-result
         :example-fn (partial ~snippet-name ~example ~(md5 as-str))})))
  ([description snippet-name example]
   `(example-snippet ~description ~snippet-name :simple ~example)))

(defmacro example-any-val
  "Create example of any type `typ` and any value `v`. Such example will be treated just as string unless you specify evaluator (see [[metadoc.evaluate]] namespace)."
  {:style/indent :defn
   :metadoc/categories #{:example}}
  [description typ v]
  `(when *process-examples*
     {:type ~typ
      :doc ~description
      :example ~v}))

(defmacro example-image
  "Create example as image, provide image url."
  {:style/indent :defn
   :metadoc/categories #{:example}}
  [description image-url]
  `(example-any-val ~description :image ~image-url))

(defmacro example-url
  "Create example as url."
  {:style/indent :defn
   :metadoc/categories #{:example}}
  [description url]
  `(example-any-val ~description :url ~url))

;;

(def ^:const ^:private ^String new-line (System/getProperty "line.separator"))

(defmulti evaluate
  "Evaluate example. Dispatch on example type.
  
  Returns example itself with added `:result` value.

  When example contain test, execute test and store result under `:test` key. `:tested` key is set to true.

  As default, evaluation returns example as a String itself."
  {:metadoc/categories #{:eval}} 
  :type)

(defmethod evaluate :default [ex] (assoc ex :result (:example ex)))

(defn- maybe-format-result
  "Format result for some specific types."
  [res]
  (cond
    (coll? res) (format-form res)
    (nil? res) "nil"
    :else res))

(defn- eval-example-fn
  "Evaluate example function(s)."
  [{:keys [example-fn]}]
  (when example-fn
    (if (coll? example-fn)
      (mapv #(%) example-fn)
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

(defmulti format-html
  "Format example to HTML. Dispatch on example type." {:metadoc/categories #{:format}} :type)

(defmethod format-html :default [r] [:div
                                     [:blockquote (:doc r)]
                                     [:pre (:result r)]])

(defn- add-comment
  "Add comment line."
  [s]
  (->> (str s)
       (s/split-lines)
       (map #(str ";;=> " %))
       (s/join new-line)))

(defn- make-code-line
  "Generate code line."
  [example evaluated? result]
  (str example
       (when evaluated?
         (str new-line (add-comment (maybe-format-result result))))))

(defn- test-result
  "Returns test result information (failed or not)."
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
  (if (= :image (:dispatch-result r))
    (html [:div
           [:blockquote (:doc r)]
           [:pre [:code {:class "hljs clojure"} (:example r)]]
           (image (:result r))])
    (format-html (assoc r :type (:dispatch-result r)))))

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

;;

(defmulti format-markdown
  "Render example to Markdown. Dispatch on example type." {:metadoc/categories #{:format}} :type)
(defmethod format-markdown :default [r] (:result r))

;;

(defmulti format-text
  "Render example to text. Dispatch on example type." {:metadoc/categories #{:format}} :type)
(defmethod format-text :default [r] (:result r))

(defmulti format-example
  "Format example. Dispatch on format type."
  {:metadoc/categories #{:format}}
  (fn [t & _] t))

(defmethod format-example :html [_ result] (format-html result))
(defmethod format-example :markdown [_ result] (format-markdown result))
(defmethod format-example :text [_ result] (format-text result))
(defmethod format-example :default [_ result] (:result result))

;; examples

(binding [*process-examples* true]
  
  (add-examples example
                (example "Simple code." (+ 1 2))
                (example "Do not evaluate." {:evaluate? false} (+ 3 4))
                (example "Test!" {:test-value 100} (* 10 10))
                (example "Access to md5-hash." {:test-value "My md5 is: 036701fecdc4f17f752110ad6bec31c5"} (str "My md5 is: " md5-hash))
                (example "How do I look inside?" (example "Access to md5-hash." {:test-value "My md5 is: 036701fecdc4f17f752110ad6bec31c5"} (str "My md5 is: " md5-hash))))
  
  (add-examples example-session
                (example-session "Execute one by one" (+ 1 2) (def ^:no-doc ^:private ignore-me 123) (let [x ignore-me] (* x x)))
                (example "What's inside above one?" (example-session "Execute one by one" (+ 1 2) (def ignore-me 123) (let [x ignore-me] (* x x))))
                (example-session "Show hashes" md5-hash (str md5-hash) (let [x md5-hash] x)))

  (add-examples md5
                (example-session "md5 sum of given string"
                                 (md5 "This is test string.")
                                 (md5 "Another string...")
                                 (md5 (md5 "...and another."))))

  (defsnippet snippet-fn
    "Print `opts` parameter and call provided function `f` with random number. See [[example-snippet]] for results."
    (do
      (println opts)
      (f (rand-int 10))))
  
  (add-examples example-snippet
                (example-snippet "Call snippet with fn" snippet-fn (fn [v] (str "sqrt of " v " = " (Math/sqrt v))))
                (example "What's inside?" (example-snippet "Call snippet with fn" snippet-fn (fn [v] (str "sqrt of " v " = " (Math/sqrt v)))))
                (example-snippet "Treat result as URL!" snippet-fn :url (fn [_] "https://github.com/generateme/metadoc"))
                (example "What's inside?" (example-snippet "Treat result as URL!" snippet-fn :url (fn [_] "https://github.com/generateme/metadoc")))
                (example-snippet "Or image" snippet-fn :image (fn [_] "img.png"))
                (example "What's inside?" (example-snippet "Or image" snippet-fn :image (fn [_] "img.png"))))

  (add-examples example-any-val
                (example-any-val "Type :anything, example :anything" :anything :anything)
                (example "Inside..." (example-any-val "Type :anything, example :anything" :anything :anything)))

  (add-examples example-image
                (example-image "Insert image below." "img.png")
                (example "Inside..." (example-image "Insert image below." "img.png"))
                (example-image "Image from url" "https://vignette.wikia.nocookie.net/mrmen/images/5/52/Small.gif/revision/latest"))

  (add-examples example-url
                (example-url "This is URL" "https://github.com/Clojure2D/clojure2d")
                (example "Inside..." (example-url "This is URL" "https://github.com/Clojure2D/clojure2d"))))
