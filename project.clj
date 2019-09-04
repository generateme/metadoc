(defproject metadoc "0.2.6"
  :description "More documentation tags in metadata"
  :url "https://github.com/generateme/metadoc"
  :license {:name "The Unlicence"
            :url "http://unlicense.org"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [zprint "0.4.16"]
                 [hiccup "2.0.0-alpha2"]
                 [enlive "1.1.6"]
                 [org.pegdown/pegdown "1.6.0"]
                 [org.clojure/tools.namespace "0.3.1"]]
  :profiles {:dev-codox {:codox {:source-uri "https://github.com/generateme/metadoc/blob/master/{filepath}#L{line}"}}})
