(ns metadoc.examples-example
  (:require [metadoc.examples :refer :all]))

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

(defsnippet metadoc.examples snippet-fn
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
  (example "Inside..." (example-url "This is URL" "https://github.com/Clojure2D/clojure2d")))
