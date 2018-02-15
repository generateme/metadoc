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

## How to use - user perspective


