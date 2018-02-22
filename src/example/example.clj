(ns example.example
  "This namespace includes all features exposed by `metadoc` library.

  What's in:

  * Code snippets
  * Examples
  * Tests from examples
  * Constants
  * Categorization"
  {:categories {:inc "With examples"
                :notinc "Without examples"}}
  (:require [metadoc.core :refer :all]
            [clojure.java.io :as io])
  (:import [java.awt Color]
           [java.awt.image BufferedImage]
           [java.awt.geom Rectangle2D$Double]
           [javax.imageio ImageIO]))

;; First some constants

(def ^:const ^String some-text "This is example text")
(def ^:const ^double ^{:doc "Just `PI` value."} pi Math/PI)
(def ^:const ^{:doc "New line characters, should be escaped."} new-line "\r\n")

;; Let's define some snippet

(defsnippet integral-snippet
  "Calculate integral value of some 2d function in ranges `(0, PI)`.
This is example of the snippet. "
  (let [step 0.01
        area (* step step)
        r (range 0 pi step)]
    (reduce (fn [curr [x y]] (+ curr (* area (f x y)))) 0
            (for [x r
                  y r]
              [x y]))))

;; let's define function with example

(defn sin-x-sin-y
  "Calculates `sin(x) * sin(y)`."
  {:categories [:inc]
   :examples [(example "Let's check result." (sin-x-sin-y 1.0 2.0))
              (example-session "List of calls. Do not evaluate" false
                (sin-x-sin-y 1.0 2.0)
                (sin-x-sin-y 0.0 0.0)
                (sin-x-sin-y pi pi))
              (example "Do not evaluate, but test against 0.0." {:evaluate false? :test-value 0.0} (sin-x-sin-y 0.0 0.0))
              (example-snippet "Calculate integral of `[0,pi]x[0,pi]` range." integral-snippet sin-x-sin-y)
              (example-url "Check integral value on WolframAlpha"
                "http://www.wolframalpha.com/input/?i=integrate+(sin(x)*sin(y))+dx+dy+x%3D0..pi+y%3D0..pi")]}
  [x y]
  (* (Math/sin x)
     (Math/sin y)))

(defn cos-x-cos-y
  "Calculates `cos(x) * cos(y)`."
  {:categories [:inc]
   :examples [(example-session "Let's calculate some values"
                (cos-x-cos-y 1.0 2.0)
                (cos-x-cos-y 0.0 0.0)
                (cos-x-cos-y pi pi))
              (example-image "Plot of the function. Taken from WolframAlpha." "plot.gif")
              (example-snippet "Calculate integral" integral-snippet cos-x-cos-y)]}
  [x y]
  (* (Math/cos x)
     (Math/cos y)))

;;

(defn function-in-some-category
  "Returns negative value od parameter `x`.
  This is function without examples."
  {:categories [:notinc]}
  [x]
  (- x))

;;

(defn custom-example
  "Return custom example map with `t` as example value."
  {:categories #{:inc}
   :examples [(example "Custom Example value" (custom-example "nothing"))]}
  [t]
  {:type :custom
   :example t
   :doc "Example documentation"})

(defn function-with-custom-example
  "Dummy function with custom example." 
  {:categories #{:inc}
   :examples [(custom-example "Here is text of the example.")]}
  [])

;;

(defn examples-with-tests
  "This function has examples with tests."
  {:categories #{:inc}
   :examples [(example "Testing against 121. Should be ok." {:test-value 121} (* 11 11))
              (example "Also testing against 121. Should fail.." {:test-value 121} (* 11 10))]}
  [])

;;

(defsnippet draw-trig
  "Draws function values and saves to file."
  (let [unique-name (str (first opts) ".png")
        canvas (BufferedImage. 200 200 BufferedImage/TYPE_INT_RGB)
        graphics (.createGraphics canvas)
        rect (Rectangle2D$Double.)
        file (io/file (str "docs/" unique-name))]
    (doto graphics
      (.setBackground (Color. (int 48) (int 66) (int 106)))
      (.clearRect 0 0 200 200)
      (.setColor Color/white))
    (dotimes [x 200] 
      (let [res (f (/ x 40.0))
            y (- 200 (+ 10 (* 90.0 (inc res))))] 
        (.setFrame rect x y 2.5 2.5)
        (.fill graphics rect)))
    (.dispose graphics)
    (ImageIO/write canvas "png" file)
    unique-name))

(defn drawing-snippet-examples
  "Draw some trig functions. Using `example-snippet` with result dispatched to `:image` type."
  {:categories [:inc]
   :examples [(example-snippet "SIN" draw-trig :image (fn [x] (Math/sin x)))
              (example-snippet "COS" draw-trig :image (fn [x] (Math/cos x)))
              (example-snippet "TAN" draw-trig :image (fn [x] (Math/tan x)))
              (example-snippet "This function" draw-trig :image drawing-snippet-examples)]}
  [x]
  (* (Math/sin x) (Math/cos (/ x 2.1))))
