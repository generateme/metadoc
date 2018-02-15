# metadoc

More documentation tags in Clojure metadata.

[Check out example result in Codox](https://generateme.github.io/metadoc/metadoc.example.html)

## Documentation

* [API doc - not finished](https://generateme.github.io/metadoc/)

## Usage

Add following dependency to your project

`[metadoc "0.0.1-SNAPSHOT"]`

To generate `Codox` docs, add also latest `Codox` dependency and configure it to use `metadoc` writer.

```
:codox {:writer metadoc.writers.codox/write-docs}
```

## What is that?

Additional metadata tags macros and tooling to enhance `:doc` documentation for your code. Including:

* Examples - add pure code snippets illustrating your functions. Simple examples can also be tests.
* Categorization - group your functions in categories (many to many)
* Constants - show all constants in one block, show also values.

Includes integration with Codox (via custom writer). But is not limited to other tools.

### How does it work?

1. Write your functions with additional metadata tags
   * :examples - list of examples, which are created by various `(example...)` macros
   * :categories - set of categories as tags, string, etc.. `#{:category1 :category2}`
2. If you need code snippet for your examples, create anywhere in your code using `(defsnippet...)` macro
3. Run doc generation (only `Codox` currently)

Internally examples are just maps of data with formatted code, nothing is really evaluated. All things happen during doc creation:

* examples are evaluated and result is stored
* if examples are marked as tests, tests are executed
* snippets, examples, constants with values, categories are collected and passed to doc tooling
* doc tool does the rest (html rendering or whatever)

Process looks like: metadata -> evaluation -> data collection -> rendering

Every step is quite simple and extensible.

## How to use - user perspective

First of all:

- setup `metadoc` and `codox` (Usage, above)
- require `metadoc.core` namespace

### Examples

Every var in your code can be illustrated by examples. Example can be code, url, image or any text with description. Documentation tools will be able to extract, evaluate and render them.

There are several types of examples. Lets start with basic one:

#### Simple

```
(example "Only one form." (+ 1 2 3 4)
(example "Only one form, do not evaluate, run test." {:evaluate? false :test-value 10} (+ 1 2 3 4))
```

#### Session

```
(example-session "List of forms." (+ 1 2) (let [x 11] x) (call-something 1 2 3))
(example-session "List of forms, no evaluation." false (+ 1 2) (let [x 11] x) (call-something 1 2 3))
```

#### Image/Url

```
(example-url "Add url" "http://clojure.org")
(example-image "Image url" "docs/image.png")
```

#### Snippet

```
(defsnippet my-snippet
  "Register snippet function which calls passed example code."
  (f 1 2))

(example-snippet "Run following code with snippet" my-snippet (fn [x y] (* x y)))
(example-snippet "Run following code with snippet, and treat result as image example" my-snippet :image (fn [x y] (str x "/" y ".png"))
```

## How to use - enhancing and integration



