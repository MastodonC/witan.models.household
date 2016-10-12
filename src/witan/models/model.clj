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
  [;; Household population
   [:input-popn-projections :group-by-5-yrs]
   [:input-resident-popn-proj :sum-by-household]
   [:group-by-5-yrs :adjust-resident-popn-proj]
   [:input-resident-popn-proj :adjust-resident-popn-proj]
   [:sum-by-household :adjust-resident-popn-proj]
   [:adjust-resident-popn-proj :calculate-household-popn]
   [:input-institutional-popn-proj :calculate-household-popn]

   ;; Households
   [:calculate-household-popn :calculate-households]
   [:input-household-representative-rates :calculate-households]
   [:calculate-households :calculate-total-households]

   ;; Household occupancy
   [:input-vacancy-rates :calculate-occupancy-rates]
   [:input-second-homes-rates :calculate-occupancy-rates]
   [:calculate-total-households :calculate-dwellings]
   [:calculate-occupancy-rates :calculate-dwellings]
   [:calculate-total-households :output-households]
   [:calculate-dwellings :output-dwellings]])

(def hh-model-catalog
  "Provides metadata for each step of the household model"
  [;; Input functions
   {:witan/name :input-popn-projections
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-popn-proj
    :witan/params {:src ""}}
   {:witan/name :input-resident-popn-proj
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-resident-popn
    :witan/params {:src ""}}
   {:witan/name :input-institutional-popn-proj
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-institutional-popn
    :witan/params {:src ""}}
   {:witan/name :input-household-representative-rates
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-household-representative-rates
    :witan/params {:src ""}}
   {:witan/name :input-vacancy-rates
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-vacancy-rates
    :witan/params {:src ""}}
   {:witan/name :input-second-homes-rates
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-second-homes-rates
    :witan/params {:src ""}}
   ;; Calculation functions
   {:witan/name :group-by-5-yrs
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/grp-popn-proj}
   {:witan/name :sum-by-household
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/sum-resident-popn}
   {:witan/name :adjust-resident-popn-proj
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/adjust-resident-proj}
   {:witan/name :calculate-household-popn
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/calc-household-popn}
   {:witan/name :calculate-households
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/calc-households}
   {:witan/name :calculate-total-households
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/calc-total-households}
   {:witan/name :calculate-occupancy-rates
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/calc-occupancy-rates}
   {:witan/name :calculate-dwellings
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/calc-dwellings}
   ;; Outputs
   {:witan/name :output-households
    :witan/version "1.0.0"
    :witan/type :output
    :witan/fn :hh-model/output-households}
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
