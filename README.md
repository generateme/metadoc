# metadoc

Documentation tags in Clojure metadata.

[Check out generated doc](https://generateme.github.io/metadoc/example.example.html)

## Documentation

* [API doc](https://generateme.github.io/metadoc/)

## Usage

Add following dependency to your project

`[metadoc "0.1.2"]`

To generate `codox` docs, add also latest `codox` dependency and configure it to use `metadoc` writer.

```
:codox {:writer metadoc.writers.codox/write-docs}
```

## What is that?

Additional metadata tags macros and tooling to enhance `:doc` documentation for your code. Including:

* Unit examples - add pure code snippets illustrating your functions. Simple examples can also be tests.
* Categorization - group your functions in categories (many to many)
* Constants - show all constants in one block, show also values.

Includes integration with Codox (via custom writer). But is not limited to other tools.

### Tags

Tags used in namespace metadata:

* `:metadoc/categories` - to map categories keys to names
* `:metadoc/snippets` - to store all snippets created with `defsnippet`

Tags used in var metadata:

* `:metadoc/categories` - to store set of categories where var belongs to
* `:metadoc/examples` - to store list of examples attached to var

### How does it work?

#### Adding examples

See [doc](https://generateme.github.io/metadoc/metadoc.examples.html)

#### Adding categorization

Add `:metadoc/categories` metatag to:

* variable - containing list/set of categories as keys. Eg. `{:metadoc/categories #{:cat1 :cat2}}`
* (optional) namespace - containing map with translation category key to name. Eg. `{:metadoc/categories {:cat1 "First category" :cat2 "Second"}}`


