(ns ^{:doc "Defines the structure of the model for it
            to be run by a workspace executor"}
 witan.models.model
  (:require [witan.workspace-api :refer [defmodel]]
            [witan.workspace-api.protocols :as p]
            [witan.workspace-api.utils :refer [map-fn-meta
                                               map-model-meta]]
            [witan.models.household :as hh]))

(def hh-model-workflow
  "Defines each step of the household model"
  [[:population :apportion-popn-by-relationship]
   [:dclg-household-popn :apportion-popn-by-relationship]
   [:dclg-institutional-popn :apportion-popn-by-relationship]
   [:dclg-institutional-popn :calc-institutional-popn]
   [:apportion-popn-by-relationship :calc-institutional-popn]
   [:apportion-popn-by-relationship :calc-household-popn]
   [:calc-institutional-popn :calc-household-popn]
   [:dclg-household-representative-rates :calc-households]
   [:calc-household-popn :calc-households]
   [:dclg-dwellings :convert-to-dwellings]
   [:vacancy-dwellings :convert-to-dwellings]
   [:calc-households :convert-to-dwellings]
   [:calc-households :output-households]
   [:calc-households :output-total-households]
   [:convert-to-dwellings :output-dwellings]])

(defn with-gss
  [id]
  (str id "_{{GSS-Code}}.csv.gz"))

(def hh-model-catalog
  "Provides metadata for each step of the household model"
  [ ;; Input functions
   {:witan/name :population
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/population
    :witan/params {:src (with-gss "witan.models.household/population")}}
   {:witan/name :dclg-household-popn
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/dclg-household-popn
    :witan/params {:src (with-gss "witan.models.household/household_population")}}
   {:witan/name :dclg-institutional-popn
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/dclg-institutional-popn
    :witan/params {:src (with-gss "witan.models.household/institutional_population")}}
   {:witan/name :dclg-household-representative-rates
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/dclg-household-representative-rates
    :witan/params {:src (with-gss "witan.models.household/household_representative_rates")}}
   {:witan/name :dclg-dwellings
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/dclg-dwellings
    :witan/params {:src (with-gss "witan.models.household/dwellings")}}
   {:witan/name :vacancy-dwellings
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/vacancy-dwellings
    :witan/params {:src (with-gss "witan.models.household/vacancy_dwellings")}}
   ;; Calculation functions
   {:witan/name :apportion-popn-by-relationship
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/apportion-popn-by-relationship}
   {:witan/name :calc-institutional-popn
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/calc-institutional-popn}
   {:witan/name :calc-household-popn
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/calc-household-popn}
   {:witan/name :calc-households
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/calc-households}
   {:witan/name :convert-to-dwellings
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/convert-to-dwellings
    :witan/params {:second-home-rate 0.0}}
   ;; Outputs
   {:witan/name :output-households
    :witan/version "1.0.0"
    :witan/type :output
    :witan/fn :hh-model/output-households}
   {:witan/name :output-total-households
    :witan/version "1.0.0"
    :witan/type :output
    :witan/fn :hh-model/output-total-households}
   {:witan/name :output-dwellings
    :witan/version "1.0.0"
    :witan/type :output
    :witan/fn :hh-model/output-dwellings}])

(defmodel household-model
  "Defines the household model. Contains metadata,
   and the model workflow + catalog."
  {:witan/name :hh-model/household-model
   :witan/version "1.0.0"}
  {:workflow hh-model-workflow
   :catalog hh-model-catalog})

(defn model-library
  "Lists all the available functions to execute each
   step of the model and list the available model."
  []
  (reify p/IModelLibrary
    (available-fns [_]
      (map-fn-meta
       hh/population-1-0-0
       hh/dclg-household-popn-1-0-0
       hh/dclg-institutional-popn-1-0-0
       hh/dclg-household-representative-rates-1-0-0
       hh/dclg-dwellings-1-0-0
       hh/vacancy-dwellings-1-0-0
       hh/apportion-popn-by-relationship-1-0-0
       hh/calc-institutional-popn-1-0-0
       hh/calc-household-popn-1-0-0
       hh/calc-households-1-0-0
       hh/convert-to-dwellings-1-0-0
       hh/output-households-1-0-0
       hh/output-total-households-1-0-0
       hh/output-dwellings-1-0-0))
    (available-models [_]
      (map-model-meta household-model))))
