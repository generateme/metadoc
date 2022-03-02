# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [0.2.10-SNAPSHOT]

* clojure 1.11
* `shutdown-agents` removed

## [0.2.9]

Pegdown replaced by Flexmark, works with `codox` 0.10.8+

## [0.2.8]

Deps bump

## [0.2.7]

### Changed

Warning: breaking! Scripts with examples should be put in `metadoc` folder now.

## [0.2.6]

Deps bump

## [0.2.5]

Deps bump

## [0.2.4]

### Added

* `example-sesssion` can accept list of values to test against

### Changed

* Updated dependencies

## [0.2.3]

### Fixed

* `example-snippet` didn't take into account parameters while generating md5-sum

## [0.2.2]

### Added

* `shutdown-agents` is called after finishing writing files

## [0.2.1]

### Example has default description "Usage"

## [0.2.0]

### Added

* Decoupling examples from code is now possible. Put your examples into `example` folder.
* Snippets can have optional namespace symbol, to transfer documentation there.

## [0.1.2]

### Added

* snippets accept additional parameters

## [0.1.1]

### Changed

* dynamic variable removed, should work out different way to enable/disable processing

## [0.1.0]

### Added

* dynamic variable *process-example* to enable/disable example processing/evaluation. Default: false (lein codox forces to set it to true).

### Fixed

* minor bugs related to empty examples lists

## [0.0.2-SNAPSHOT]

### Changed

- Tags are qualified now (`:examples` -> `:metadoc/examples`)

## [0.0.1-SNAPSHOT]

### Added
- Initial code: examples, categories, constants, snippets

