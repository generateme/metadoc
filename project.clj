(defproject metadoc "0.2.4-SNAPSHOT"
  :description "More documentation tags in metadata"
  :url "https://github.com/generateme/metadoc"
  :license {:name "The Unlicence"
            :url "http://unlicense.org"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [zprint "0.4.10"]
                 [hiccup "1.0.5"]
                 [enlive "1.1.6"]
                 [org.pegdown/pegdown "1.6.0"]
                 [org.clojure/tools.namespace "0.2.11"]]
  :profiles {:dev {:plugins [[refactor-nrepl "2.4.0"]
                             [cider/cider-nrepl "0.19.0-SNAPSHOT"]
                             [lein-codox "0.10.4"]]
                   :dependencies [[codox-theme-rdash "0.1.2"]]
                   :source-paths ["example"]
                   :codox {:themes [:rdash]
                           :metadata {:doc/format :markdown}
                           :output-path "docs/"
                           :source-paths ["src"]
                           :source-uri "https://github.com/generateme/metadoc/blob/master/{filepath}#L{line}"
                           :writer metadoc.writers.codox/write-docs}}})
