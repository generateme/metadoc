(ns metadoc.writers.codox
  "Documentation writer that outputs HTML."
  (:use [hiccup core page element util])
  (:import [java.net URLEncoder]
           [com.vladsch.flexmark.ast Link LinkRef]
           [com.vladsch.flexmark.ext.wikilink WikiLink WikiLinkExtension]
           [com.vladsch.flexmark.html HtmlRenderer
            HtmlRenderer$HtmlRendererExtension LinkResolver LinkResolverFactory]
           [com.vladsch.flexmark.html.renderer LinkResolverBasicContext
            LinkStatus ResolvedLink]
           [com.vladsch.flexmark.parser Parser]
           [com.vladsch.flexmark.profile.pegdown Extensions
            PegdownOptionsAdapter]
           [com.vladsch.flexmark.util.misc Extension])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [net.cgrand.enlive-html :as enlive-html]
            [net.cgrand.jsoup :as jsoup]
            [metadoc.examples :as ex]
            [metadoc.reader :as er]))

(def ^:private escape-map {(char 0xffff) "\\0xffff"
                         (char 13) "\\r"
                         (char 10) "\\n"})

(def enlive-operations
  {:append     enlive-html/append
   :prepend    enlive-html/prepend
   :after      enlive-html/after
   :before     enlive-html/before
   :substitute enlive-html/substitute})

(defn- enlive-transformer [[op & args]]
  (apply (enlive-operations op) (map enlive-html/html args)))

(defn- enlive-transform [nodes transforms]
  (reduce
   (fn [out [s t]]
     (enlive-html/transform out s (enlive-transformer t)))
   nodes
   (partition 2 transforms)))

(defn- enlive-emit [nodes]
  (apply str (enlive-html/emit* nodes)))

(defn- enlive-parse [^String s]
  (let [stream (io/input-stream (.getBytes s "UTF-8"))]
    (enlive-html/html-resource stream {:parser jsoup/parser})))

(defn- transform-html [project s]
  (-> (enlive-parse s)
      (enlive-transform (-> project :html :transforms))
      (enlive-emit)))

(defn- var-id [var]
  (str "var-" (-> var name URLEncoder/encode (str/replace "%" "."))))

(def ^:private url-regex
  #"((https?|ftp|file)://[-A-Za-z0-9+()&@#/%?=~_|!:,.;]+[-A-Za-z0-9+()&@#/%=~_|])")

(defn- add-anchors [text]
  (when text
    (str/replace text url-regex "<a href=\"$1\">$1</a>")))

(defmulti format-docstring
  "Format the docstring of a var or namespace into HTML."
  (fn [_ _ var] (:doc/format var))
  :default :plaintext)

(defmethod format-docstring :plaintext [_ _ metadata]
  [:pre.plaintext (add-anchors (h (:doc metadata)))])

#_(def ^:private pegdown
    (PegDownProcessor.
     (bit-or Extensions/AUTOLINKS
             Extensions/QUOTES
             Extensions/SMARTS
             Extensions/STRIKETHROUGH
             Extensions/TABLES
             Extensions/FENCED_CODE_BLOCKS
             Extensions/WIKILINKS
             Extensions/DEFINITIONS
             Extensions/ABBREVIATIONS
             Extensions/ATXHEADERSPACE
             Extensions/RELAXEDHRULES
             Extensions/EXTANCHORLINKS)
     2000))

(defn- public-vars
  "Return a list of all public var names in a collection of namespaces from one
  of the reader functions."
  [namespaces]
  (for [ns  namespaces
        var (:publics ns)
        v   (concat [var] (:members var))]
    (symbol (str (:name ns)) (str (:name v)))))

(def ^:private re-chars (set "\\.*+|?()[]{}$^"))

(defn re-escape
  "Escape a string so it can be safely placed in a regex."
  [s]
  (str/escape s #(when (re-chars %) (str \\ %))))

(defn- search-vars
  "Find the best-matching var given a partial var string, a list of namespaces,
  and an optional starting namespace."
  [namespaces partial-var & [starting-ns]]
  (let [regex   (if (.contains ^String partial-var "/")
                  (re-pattern (str (re-escape partial-var) "$"))
                  (re-pattern (str "/" (re-escape partial-var) "$")))
        matches (filter
                 #(re-find regex (str %))
                 (public-vars namespaces))]
    (or (first (filter #(= (str starting-ns) (namespace %)) matches))
        (first matches))))

(defn- find-wiki-link [project ns text]
  (let [ns-strs (map (comp str :name) (:namespaces project))]
    (if (contains? (set ns-strs) text)
      (str text ".html")
      (when-let [var (search-vars (:namespaces project) text (:name ns))]
        (str (namespace var) ".html#" (var-id var))))))

#_(defn- parse-wikilink [text]
    (let [pos (.indexOf text "|")]
      (if (>= pos 0)
        [(subs text 0 pos) (subs text (inc pos))]
        [text text])))

(defn- absolute-url? [url]
  (re-find #"^([a-z]+:)?//" url))

(defn- fix-markdown-url [url]
  (if-not (absolute-url? url)
    (str/replace url #"\.(md|markdown)$" ".html")
    url))

#_(defn- encode-title [rendering title]
    (if (str/blank? title)
      rendering
      (.withAttribute rendering "title" (FastEncoder/encode title))))

#_(defn- link-renderer [project & [ns]]
    (proxy [LinkRenderer] []
      (render
        ([node]
         (if (instance? WikiLinkNode node)
           (let [[page text] (parse-wikilink (.getText node))]
             (LinkRenderer$Rendering. (find-wikilink project ns page) text))
           (proxy-super render node)))
        ([node text]
         (if (instance? ExpLinkNode node)
           (-> (LinkRenderer$Rendering. (fix-markdown-url (.url node)) text)
               (encode-title (.title node)))
           (proxy-super render node text)))
        ([node url title text]
         (if (instance? RefLinkNode node)
           (-> (LinkRenderer$Rendering. (fix-markdown-url url) text)
               (encode-title title))
           (proxy-super render node url title text))))))

(defn- update-link-url [^ResolvedLink link f]
  (-> link
      (.withStatus LinkStatus/VALID)
      (.withUrl (f (.getUrl link)))))

(defn- correct-internal-links [node link project ns]
  (condp instance? node
    WikiLink (update-link-url link #(find-wiki-link project ns %))
    LinkRef  (update-link-url link fix-markdown-url)
    Link     (update-link-url link fix-markdown-url)
    link))

(defn- make-renderer-extension
  [project ns]
  (reify HtmlRenderer$HtmlRendererExtension
    (rendererOptions [_ _])
    (extend [_ htmlRendererBuilder _]
      (.linkResolverFactory
       htmlRendererBuilder
       (reify LinkResolverFactory
         (getAfterDependents [_] nil)
         (getBeforeDependents [_] nil)
         (affectsGlobalScope [_] false)
         (^LinkResolver apply [_ ^LinkResolverBasicContext _]
          (reify LinkResolver
            (resolveLink [_ node _ link]
              (correct-internal-links node link project ns)))))))))

(defn- make-flexmark-options
  [project ns]
  (-> (PegdownOptionsAdapter/flexmarkOptions
       (bit-or Extensions/AUTOLINKS
               Extensions/QUOTES
               Extensions/SMARTS
               Extensions/STRIKETHROUGH
               Extensions/TABLES
               Extensions/FENCED_CODE_BLOCKS
               Extensions/WIKILINKS
               Extensions/DEFINITIONS
               Extensions/ABBREVIATIONS
               Extensions/ATXHEADERSPACE
               Extensions/RELAXEDHRULES
               Extensions/EXTANCHORLINKS)
       (into-array Extension [(make-renderer-extension project ns)]))
      (.toMutable)
      (.set WikiLinkExtension/LINK_FIRST_SYNTAX true)
      (.toImmutable)))

(defn- markdown-to-html
  ([doc project]
   (markdown-to-html doc project nil))
  ([doc project ns]
   (let [options  (make-flexmark-options project ns)
         parser   (.build (Parser/builder options))
         renderer (.build (HtmlRenderer/builder options))]
     (->> doc (.parse parser) (.render renderer)))))

(defn- format-markdown 
  [doc project ns]
  #_(.markdownToHtml pegdown doc (link-renderer project ns))
  (markdown-to-html doc project ns))

(defmethod format-docstring :markdown [project ns metadata]
  [:div.markdown
   (when-let [doc (:doc metadata)]
     (format-markdown doc project ns))])

(defn- ns-filename [namespace]
  (str (:name namespace) ".html"))

(defn- ns-filepath [output-dir namespace]
  (str output-dir "/" (ns-filename namespace)))

(defn- doc-filename [doc]
  (str (:name doc) ".html"))

(defn- doc-filepath [output-dir doc]
  (str output-dir "/" (doc-filename doc)))

(defn- var-uri [namespace var]
  (str (ns-filename namespace) "#" (var-id (:name var))))

(defn- get-source-uri [source-uris path]
  (some (fn [[re f]] (when (re-find re path) f)) source-uris))

(defn- uri-basename [path]
  (second (re-find #"/([^/]+?)$" path)))

(defn- uri-path [path]
  (str/replace (str path) java.io.File/separator "/"))

(defn- var-source-uri
  [{:keys [source-uri version]}
   {:keys [path file line]}]
  (let [path (uri-path path)
        uri  (if (map? source-uri) (get-source-uri source-uri path) source-uri)]
    (-> uri
        (str/replace "{filepath}"  path)
        (str/replace "{classpath}" (uri-path file))
        (str/replace "{basename}"  (uri-basename path))
        (str/replace "{line}"      (str line))
        (str/replace "{version}"   version))))

(defn- split-ns [namespace]
  (str/split (str namespace) #"\."))

(defn- namespace-parts [namespace]
  (->> (split-ns namespace)
       (reductions #(str %1 "." %2))
       (map symbol)))

(defn- add-depths [namespaces]
  (->> namespaces
       (map (juxt identity (comp count split-ns)))
       (reductions (fn [[_ ds] [ns d]] [ns (cons d ds)]) [nil nil])
       (rest)))

(defn- add-heights [namespaces]
  (for [[ns ds] namespaces]
    (let [d (first ds)
          h (count (take-while #(not (or (= d %) (= (dec d) %))) (rest ds)))]
      [ns d h])))

(defn- add-branches [namespaces]
  (->> (partition-all 2 1 namespaces)
       (map (fn [[[ns d0 h] [_ d1 _]]] [ns d0 h (= d0 d1)]))))

(defn- namespace-hierarchy [namespaces]
  (->> (map :name namespaces)
       (sort)
       (mapcat namespace-parts)
       (distinct)
       (add-depths)
       (add-heights)
       (add-branches)))

(defn- index-by [f m]
  (into {} (map (juxt f identity) m)))

;; The values in ns-tree-part are chosen for aesthetic reasons, based
;; on a text size of 15px and a line height of 31px.

(defn- ns-tree-part [height]
  (if (zero? height)
    [:span.tree [:span.top] [:span.bottom]]
    (let [row-height 31
          top        (- 0 21 (* height row-height))
          height     (+ 0 30 (* height row-height))]
      [:span.tree {:style (str "top: " top "px;")}
       [:span.top {:style (str "height: " height "px;")}]
       [:span.bottom]])))

(defn- index-link [_ on-index?]
  (list
   [:h3.no-link [:span.inner "Project"]]
   [:ul.index-link
    [:li.depth-1 {:class (when on-index? "current")}
     (link-to "index.html" [:div.inner "Index"])]]))

(defn- topics-menu [project current-doc]
  (when-let [docs (seq (:documents project))]
    (list
     [:h3.no-link [:span.inner "Topics"]]
     [:ul
      (for [doc docs]
        [:li.depth-1
         {:class (if (= doc current-doc) " current")}
         (link-to (doc-filename doc) [:div.inner [:span (h (:title doc))]])])])))

(defn- nested-namespaces [namespaces current-ns]
  (let [ns-map (index-by :name namespaces)]
    [:ul
     (for [[name depth height branch?] (namespace-hierarchy namespaces)]
       (let [class  (str "depth-" depth (when branch? " branch"))
             short  (last (split-ns name))
             inner  [:div.inner (ns-tree-part height) [:span (h short)]]]
         (if-let [ns (ns-map name)]
           (let [class (str class (when (= ns current-ns) " current"))]
             [:li {:class class} (link-to (ns-filename ns) inner)])
           [:li {:class class} [:div.no-link inner]])))]))

(defn- flat-namespaces [namespaces current-ns]
  [:ul
   (for [ns (sort-by :name namespaces)]
     [:li.depth-1
      {:class (when (= ns current-ns) "current")}
      (link-to (ns-filename ns) [:div.inner [:span (h (:name ns))]])])])

(defn- namespace-list-type [project]
  (let [default (if (> (-> project :namespaces count) 1) :nested :flat)]
    (get-in project [:html :namespace-list] default)))

(defn- namespaces-menu [project current-ns]
  (let [namespaces (:namespaces project)]
    (list
     [:h3.no-link [:span.inner "Namespaces"]]
     (case (namespace-list-type project) 
       :flat   (flat-namespaces namespaces current-ns)
       :nested (nested-namespaces namespaces current-ns)))))

(defn- primary-sidebar [project & [current]]
  [:div.sidebar.primary
   (index-link project (nil? current))
   (topics-menu project current)
   (namespaces-menu project current)])

(defn- sorted-public-vars [namespace]
  (sort-by (comp str/lower-case :name) (:publics namespace)))

(defn- vars-sidebar [namespace]
  [:div.sidebar.secondary
   [:h3 (link-to "#top" [:span.inner "Public Vars"])]
   [:ul
    (for [var (sorted-public-vars namespace)]
      (list*
       [:li.depth-1
        (link-to (var-uri namespace var) [:div.inner [:span (h (:name var))]])]
       (for [mem (:members var)]
         (let [branch? (not= mem (last (:members var)))
               class   (if branch? "depth-2 branch" "depth-2")
               inner   [:div.inner (ns-tree-part 0) [:span (h (:name mem))]]]
           [:li {:class class}
            (link-to (var-uri namespace mem) inner)]))))]])

(def ^:private default-meta
  [:meta {:charset "UTF-8"}])

(defn- project-title [project]
  [:span.project-title
   [:span.project-name (h (:name project))] " "
   [:span.project-version (h (:version project))]])

(defn- header [project]
  [:div#header
   [:h2 "Generated by " (link-to "https://github.com/weavejester/codox" "Codox")]
   [:h1 (link-to "index.html" (project-title project))]])

(defn- package [project]
  (when-let [p (:package project)]
    (if (= (namespace p) (name p))
      (symbol (name p))
      p)))

(defn- add-ending [^String s ^String ending]
  (if (.endsWith s ending) s (str s ending)))

(defn- strip-prefix [s prefix]
  (when s (str/replace s (re-pattern (str "(?i)^" prefix)) "")))

(defn- summary
  "Return the summary of a docstring.
   The summary is the first portion of the string, from the first
   character to the first page break (\f) character OR the first TWO
   newlines."
  [s]
  (when s
    (->> (str/trim s)
         (re-find #"(?s).*?(?=\f)|.*?(?=\n\n)|.*"))))

(defn- category-line 
  [project namespace categories n]
  (interpose " " (for [v (sort (n categories))]
                   [:a {:href (find-wiki-link project namespace (str v))} v])))

(defn- categories-part 
  [project namespace]
  (let [categories (:categories-list namespace)]
    (when-not (empty? categories)
      (let [categories-names (:metadoc/categories namespace)]
        [:div.markdown
         [:h4 "Categories"]
         [:ul
          (for [n (sort (keys categories))
                :let [nn (or (n categories-names) (name n))]
                :when (not (= n :other))]
            [:li nn ": " (category-line project namespace categories n)])]
         (when (:other categories) 
           [:p "Other vars: " (category-line project namespace categories :other)])]))))


(defn- index-page [project]
  (html5
   [:head
    default-meta
    [:title (h (:name project)) " " (h (:version project))]]
   [:body
    (header project)
    (primary-sidebar project)
    [:div#content.namespace-index
     [:h1 (project-title project)]
     (when-let [license (-> (get-in project [:license :name]) (strip-prefix "the "))]
       [:h5.license
        "Released under the "
        (if-let [url (get-in project [:license :url])]
          (link-to url license)
          license)])
     (when-let [description (:description project)]
       [:div.doc [:p (h (add-ending description "."))]])
     (when-let [package (package project)]
       (list
        [:h2 "Installation"]
        [:p "To install, add the following dependency to your project or build file:"]
        [:pre.deps (h (str "[" package " " (pr-str (:version project)) "]"))]))
     (when-let [docs (seq (:documents project))]
       (list
        [:h2 "Topics"]
        [:ul.topics
         (for [doc docs]
           [:li (link-to (doc-filename doc) (h (:title doc)))])]))
     [:h2 "Namespaces"]
     (for [namespace (sort-by :name (:namespaces project))]
       [:div.namespace
        [:h3 (link-to (ns-filename namespace) (h (:name namespace)))]
        [:div.doc (format-docstring project nil (update-in namespace [:doc] summary))]
        [:div.doc (categories-part project namespace)]])]]))

(defmulti format-document
  "Format a document into HTML."
  (fn [_ doc] (:format doc)))

(defmethod format-document :markdown [project doc]
  #_[:div.markdown (.markdownToHtml pegdown (:content doc) (link-renderer project))]
  [:div.markdown (markdown-to-html (:content doc) project)])

(defn- document-page [project doc]
  (html5
   [:head
    default-meta
    [:title (h (:title doc))]]
   [:body
    (header project)
    (primary-sidebar project doc)
    [:div#content.document
     [:div.doc (format-document project doc)]]]))

(defn- var-usage [var]
  (for [arglist (:arglists var)]
    (list* (:name var) arglist)))

(defn- added-and-deprecated-docs [var]
  (list
   (when-let [added (:added var)]
     [:h4.added "added in " added])
   (when-let [deprecated (:deprecated var)]
     [:h4.deprecated "deprecated" (when (string? deprecated) (str " in " deprecated))])))

(defn- remove-namespaces [x namespaces]
  (if (and (symbol? x) (contains? namespaces (namespace x)))
    (symbol (name x))
    x))

(defn- normalize-types [types]
  (read-string (pr-str types)))

(defn- pprint-str [x]
  (with-out-str (pp/pprint x)))

(defn- type-sig [namespace var]
  (let [implied-namespaces #{(str (:name namespace)) "clojure.core.typed"}]
    (->> (:type-sig var)
         (normalize-types)
         (walk/postwalk #(remove-namespaces % implied-namespaces))
         (pprint-str))))

(defn- escape-value
  [s]
  (escape-html (str/escape (str s) escape-map)))

(defn- var-docs [project namespace var]
  (let [constant-value ((:name var) (:constants namespace))
        examples ((:name var) (:examples namespace))]
    [:div.public.anchor {:id (h (var-id (:name var)))}
     [:h3 (h (:name var))]
     (when-not (= (:type var) :var)
       [:h4.type (name (:type var))])
     (when constant-value
       [:h4.dynamic "const"]) 
     (when (:dynamic var)
       [:h4.dynamic "dynamic"])
     (added-and-deprecated-docs var)
     (when (:type-sig var)
       [:div.type-sig
        [:pre (h (type-sig namespace var))]])
     [:div.usage
      (for [form (var-usage var)]
        [:code (h (pr-str form))])]
     (when constant-value
       [:div [:div.markdown [:code {:class "hljs clojure"} ";;=> " (escape-value constant-value)]]])
     [:div.doc (format-docstring project namespace var)]
     (when (seq examples)
       [:div.markdown
        [:h4 "Examples"]
        (for [ex examples] 
          (ex/format-example :html (update ex :doc #(format-markdown % project namespace))))])
     (when-let [members (seq (:members var))]
       [:div.members
        [:h4 "members"]
        [:div.inner
         (let [project (dissoc project :source-uri)]
           (map (partial var-docs project namespace) members))]])
     (when (:source-uri project)
       (if (:path var)
         [:div.src-link (link-to (var-source-uri project var) "view source")]
         (println "Could not generate source link for" (:name var))))]))

(defn- constants-part 
  [project namespace]
  (when-not (empty? (:constants namespace))
    [:div.markdown
     [:h4 "Constants"]
     [:ul
      (for [[n v] (sort (:constants namespace))]
        [:li [:a {:href (find-wiki-link project namespace (str n))} n] " = " [:code (escape-value v)]])]]))

(defn- snippets-part 
  [project namespace]
  (when-let [snippets (seq (filter (comp not :hidden) (:snippets namespace)))]
    [:div.markdown
     [:h4 "Code snippets"]
     (for [{:keys [doc fn-str]} (vals snippets)]
       [:div
        [:blockquote (format-markdown doc project namespace)]
        [:pre [:code fn-str]]])]))

(defn- namespace-page [project namespace]
  (html5
   [:head
    default-meta
    [:title (h (:name namespace)) " documentation"]]
   [:body
    (header project)
    (primary-sidebar project namespace)
    (vars-sidebar namespace)
    [:div#content.namespace-docs
     [:h1#top.anchor (h (:name namespace))]
     (added-and-deprecated-docs namespace)
     [:div.doc
      (format-docstring project namespace namespace)
      (categories-part project namespace)
      (constants-part project namespace)
      (snippets-part project namespace)] 
     (for [var (sorted-public-vars namespace)]
       (var-docs project namespace var))]]))

#_(defn- mkdirs [output-dir & dirs]
    (doseq [dir dirs]
      (.mkdirs (io/file output-dir dir))))

(defn- write-index [output-dir project]
  (spit (io/file output-dir "index.html") (transform-html project (index-page project))))

(defn- write-namespaces [output-dir project]
  (doseq [namespace (:namespaces project)]
    (spit (ns-filepath output-dir namespace)
          (transform-html project (namespace-page project namespace)))))

(defn- write-documents [output-dir project]
  (doseq [document (:documents project)]
    (spit (doc-filepath output-dir document)
          (transform-html project (document-page project document)))))

(defn- theme-path [theme]
  (let [theme-name (if (vector? theme) (first theme) theme)]
    (str "codox/theme/" (name theme-name))))

(defn- insert-params [theme-data theme]
  (let [params   (if (vector? theme) (or (second theme) {}) {})
        defaults (:defaults theme-data {})]
    (assert (map? params) "Theme parameters must be a map")
    (assert (map? defaults) "Theme defaults must be a map")
    (->> (dissoc theme-data :defaults)
         (walk/postwalk #(if (keyword? %) (params % (defaults % %)) %)))))

(defn- read-theme [theme]
  (some-> (theme-path theme)
          (str "/theme.edn")
          io/resource slurp
          edn/read-string
          (insert-params theme)))

(defn- make-parent-dir [file]
  (-> file io/file .getParentFile .mkdirs))

(defn- copy-resource [resource output-path]
  (io/copy (io/input-stream (io/resource resource)) output-path))

(defn- copy-theme-resources [output-dir project]
  (doseq [theme (:themes project)]
    (let [root (theme-path theme)]
      (doseq [path (:resources (read-theme theme))]
        (let [output-file (io/file output-dir path)]
          (make-parent-dir output-file)
          (copy-resource (str root "/" path) output-file))))))

(defn- apply-one-theme [project theme]
  (if-let [{:keys [transforms]} (read-theme theme)]
    (update-in project [:html :transforms] concat transforms)
    (throw (IllegalArgumentException. (format "Could not find Codox theme: %s" theme)))))

(defn- apply-theme-transforms [{:keys [themes] :as project}]
  (reduce apply-one-theme project themes))

;; update namespaces and variables

(defn- maybe-assoc
  [project ns n key-in key-target f]
  (if-not (contains? (:exclude-metadoc project) key-in)
    (assoc n key-target (f ns))
    n))

(defn- add-sections
  "Add constants, categories and snippets"
  [project]
  (assoc project :namespaces
         (map #(let [ns (find-ns (:name %))
                     ma (partial maybe-assoc project ns)]
                 (as-> % n
                   (ma n :constants :constants er/extract-constants)
                   (ma n :examples :examples er/extract-examples)
                   (ma n :categories :categories-list er/extract-categories)
                   (ma n :snippets :snippets er/extract-snippets)))
              (:namespaces project))))

(defn write-docs
  "Take raw documentation info and turn it into formatted HTML."
  [{:keys [output-path] :as project}]
  (er/load-examples)
  (let [project (-> project
                    (apply-theme-transforms)
                    (add-sections))]
    (doto output-path
      (copy-theme-resources project)
      (write-index project)
      (write-namespaces project)
      (write-documents project))
    (println "Done")))

