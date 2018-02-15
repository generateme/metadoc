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

Additional metadata tags macros and tooling to enhance `:doc` documentation for your code.

* Examples - add pure code snippets illustrating your functions. Simple examples can also be tests.
* Categorization - group your functions in categories (many to many)
* Constants - show all constants in one block, show also values.

Includes integration with Codox (via custom writer). But is not limited to others.

### How does it work?

1. Write your functions with additional metadata tags
   * :examples - list of examples, which are created by various `(example...)` macros
   * :categories - set of categories as tags, string, etc.. `#{:category1 :category2}`
2. If you need code snippet for your examples, created anywhere in your code using `(defsnippet...)` macro
3. Run doc generation (only `Codox` currently)

Internally examples are just maps of data with formatted code, nothing is really evaluated. All things are happen during doc creation:

* examples are evaluated and result is stored
* if examples are marked as tests, tests are executed
* snippets, examples, constants with values, categories are collected and passed to doc tooling
* doc tool does the rest (html rendering or whatever)

Process looks like: metadata -> evaluation -> data collection -> rendering

## How to use - user perspective

## How to use - enhancing and integration



