# witan.models.household

[![Build Status](https://circleci.com/gh/MastodonC/witan.models.household.svg?style=shield)](https://circleci.com/gh/MastodonC/witan.models.household) [![Dependencies Status](https://jarkeeper.com/MastodonC/witan.models.household/status.svg)](https://jarkeeper.com/MastodonC/witan.models.household)


## Table of Contents:

* [Description](#description)

* [Contribute to `witan.models.household`](#contribute-to-witanmodelshousehold)
  * [General practices](#general-practices)
  * [Development tools](#development-tools)

* [License](#license)


## Description

`witan.models.household` is a Clojure library to determine the number of households and dwellings based on population projections. More information about the methodology can be found in the [docs](https://github.com/MastodonC/witan.models.household/blob/master/doc/intro.md).

See [`witan.models.demography`](https://github.com/MastodonC/witan.models.demography) for a Clojure library to build population projections.

These models will be used on MastodonC's [Witan](http://www.mastodonc.com/products/witan/) city decision-making platform. They can also be used independently of Witan as a standalone modelling library.

Current status:
* Household model: First release of a minimal version coming soon!

## Contribute to `witan.models.household`

### General practices

If you wish to contribute to `witan.models.household`, please read the following guidelines.

#### Github guidelines
* **Fork this repository** (or clone it if you have writing rights to the reposotory).
* **Create a new branch**. Let's try and keep the following naming conventions for branches:
  * `feature/`, example: `feature/calculate-vacancy-rates`
  * `doc/`, example: `doc/contributor-best-practices`
  * `fix/`, example: `fix/run-model-from-cli`
  * `tidy-up/`, example: `tidy-up/upgrade-deps`

  This way, when you see a branch starting by `fix/` we know something is broken and someone is repairing it.
* **Keep branches short** so that the reviewing process is easier and faster
* Start a pull request (PR) as early as possible. You can add a `WIP` in the title to specify it's in progress.
* Describe the aim of your changes in the PR description box.
* Before asking for a review of your PR:
  * **run all the tests** from the commmand line with `$ lein test`
  * **run the lint tools** [`Eastwood`](https://github.com/jonase/eastwood) and [`Kibit`](https://github.com/jonase/kibit) with `$ lein eastwood` and `$ lein kibit`.
  * **format your code** with [`Cljfmt`](https://github.com/weavejester/cljfmt) tool with `lein cljfmt check` followed by `lein cljfmt fix`.

#### Coding guidelines

* Write unit tests, docstrings, and documentation.
* Try to not have data changes and code changes in the same commit, and preferably not the same branch, as the data tends to swamp the code and hinder reviewing.
* Avoid modifying a file that is being modified on another branch.
* Avoid changing the name of a file while someone is working on another branch.
* We moved away from using Incanter library.
  To manipulate `core.matrix` datasets look for functions:
  * from `core.matrix` in [`core.matrix.datasets`](https://github.com/mikera/core.matrix/blob/develop/src/main/clojure/clojure/core/matrix/dataset.clj)
  * from the `witan.workspace-api` dependency in [`witan.datasets`](https://github.com/MastodonC/witan.workspace-api/blob/master/src/witan/datasets.clj)
* Have a look at the following paragraph for useful development tools.
* Commit and push your changes often using descriptive commit messages. And squash commits (as much as possible/necessary) before asking for a review.

## Development tools

When combining functions into a model there are useful tools to take advantage of, thanks to dependencies for `witan.workspace-executor` and `witan.workspace-api`.

#### To visualise a model workflow, you need to:

1) Install `Graphviz`:

- Ubuntu: `$ sudo apt-get install graphviz`

- MacOS: `$ brew install graphviz`

For any OS you should also be able to install it with "pip": `$ pip install graphviz`.

2) Use the `view-workflow` function using the household model workflow (hh-model-workflow)
as follows:

```Clojure
(witan.workspace-executor.core/view-workflow hh-model-workflow)
```
#### To print logs, use the `set-api-logging!` function:

```Clojure
(witan.workspace-api/set-api-logging! println)
```
Whenever a `defworkflowfn` is called logs will be written to your repl or terminal. It's very  useful for debugging purpose.

Turn it off with:
```Clojure
(witan.workspace-api/set-api-logging! identity)
```

## Splitting and Uploading data

By default, the data for the CCM is amalgamated into single data files.
To split the files by GSS code, use the following command:

```
lein split-data
```

To upload all the CSV files to S3 (gzipped), use the following command:

```
lein upload-data
```

This assumes a valid AWS profile, called 'witan', is installed. For ease, these commands can be chained like so:

```
lein do split-data, upload-data
```

### Running the model in the workspace executor

The test namespace currently checks the model can actually be run by the workspace executor.

When running the model in the workspace executor from the repl it's very useful to turn on the logs for the model, using:
```Clojure
(witan.workspace-api/set-api-logging! println)
```


**Note**:

The version of the code containing the structure of the model for it to run using the workspace executor can be accessed by selecting the tag `model-skeleton`.


## License

Copyright © 2016 MastodonC Ltd

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.