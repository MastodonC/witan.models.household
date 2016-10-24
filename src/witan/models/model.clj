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
  [[:input-popn :apportion-popn-by-relationship]
   [:input-dclg-household-popn :apportion-popn-by-relationship]
   [:input-dclg-institutional-popn :apportion-popn-by-relationship]
   [:input-dclg-institutional-popn :calc-institutional-popn]
   [:apportion-popn-by-relationship :calc-institutional-popn]
   [:apportion-popn-by-relationship :calc-household-popn]
   [:calc-institutional-popn :calc-household-popn]
   [:input-dclg-household-representative-rates :calc-households]
   [:calc-household-popn :calc-households]
   [:input-dwellings :convert-to-dwellings]
   [:input-vacancy-dwellings :convert-to-dwellings]
   [:calc-households :convert-to-dwellings]
   [:calc-households :output-households]
   [:calc-households :output-total-households]
   [:convert-to-dwellings :output-dwellings]])

(def hh-model-catalog
  "Provides metadata for each step of the household model"
  [ ;; Input functions
   {:witan/name :input-popn
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-popn-proj
    :witan/params {:src ""}}
   {:witan/name :input-dclg-household-popn
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-dclg-household-popn
    :witan/params {:src ""}}
   {:witan/name :input-dclg-institutional-popn
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-dclg-institutional-popn
    :witan/params {:src ""}}
   {:witan/name :input-dclg-household-representative-rates
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-dclg-household-representative-rates
    :witan/params {:src ""}}
   {:witan/name :input-dclg-dwellings
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-dclg-dwellings
    :witan/params {:src ""}}
   {:witan/name :input-vacancy-dwellings
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-vacancy-dwellings
    :witan/params {:src ""}}]
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
   :witan/fn :hh-model/output-dwellings})

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
       hh/get-popn-proj-1-0-0
       hh/get-resident-popn-1-0-0
       hh/get-institutional-popn-1-0-0
       hh/get-household-representative-rates-1-0-0
       hh/get-vacancy-rates-1-0-0
       hh/get-second-homes-rates-1-0-0
       hh/grp-popn-proj-1-0-0
       hh/sum-resident-popn-1-0-0
       hh/adjust-resident-proj-1-0-0
       hh/calc-household-popn-1-0-0
       hh/calc-households-1-0-0
       hh/calc-total-households-1-0-0
       hh/calc-occupancy-rates-1-0-0
       hh/calc-dwellings-1-0-0
       hh/output-households-1-0-0
       hh/output-dwellings-1-0-0))
    (available-models [_]
      (map-model-meta household-model))))
