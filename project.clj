(defproject metadoc "0.2.9"
  :description "More documentation tags in metadata"
  :url "https://github.com/generateme/metadoc"
  :license {:name "The Unlicence"
            :url "http://unlicense.org"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [zprint "1.2.0"]
                 [hiccup "2.0.0-alpha2"]
                 [enlive "1.1.6"]
                 [com.vladsch.flexmark/flexmark "0.62.2"]
                 [com.vladsch.flexmark/flexmark-profile-pegdown "0.62.2"]
                 [com.vladsch.flexmark/flexmark-util-misc"0.62.2"]
                 [org.clojure/tools.namespace "1.2.0"]]
  :profiles {:dev-codox {:codox {:source-uri "https://github.com/generateme/metadoc/blob/master/{filepath}#L{line}"}}})
