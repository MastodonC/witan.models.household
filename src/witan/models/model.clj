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
  [;; Adjust Household population
   ;; ccm popn proj
   [:input-popn-proj :group-by-5-yrs]
   [:group-by-5-yrs :adjust-household-popn-proj]
   [:group-by-5-yrs :adjust-institutional-popn-proj]
   ;; dclg household proj
   [:input-household-popn-proj :calculate-household-proportions]
   [:calculate-household-proportions :adjust-household-popn-proj]
   [:input-household-popn-proj :calculate-resident-popn-proj]
   [:calculate-resident-popn-proj :calculate-household-proportions]
   ;; dclg institutional proj
   [:input-institutional-popn-proj :calculate-resident-popn-proj]
   [:input-institutional-popn-proj :calculate-institutional-proportions]
   [:calculate-institutional-proportions :adjust-institutional-popn-proj]
   ;; dclg resident proj
   [:calculate-resident-popn-proj :calculate-institutional-proportions]
   [:adjust-household-popn-proj :calc-adj-resident-popn]
   [:adjust-institutional-popn-proj :calc-adj-resident-popn]
   [:calc-adj-resident-popn :output-adj-resident-popn]

   ;; Total Households
   [:adjust-household-popn-proj :calculate-households]
   [:input-household-formation-rates :calculate-households]
   [:calculate-households :calculate-total-households]
   [:calculate-total-households :output-total-households]

   ;; Dwellings
   [:input-vacancy-rates :calculate-occupancy-rates]
   [:input-second-homes-rates :calculate-occupancy-rates]
   [:calculate-total-households :calculate-dwellings]
   [:calculate-occupancy-rates :calculate-dwellings]
   [:calculate-dwellings :output-dwellings]])

(def hh-model-catalog
  "Provides metadata for each step of the household model"
  [;; Input functions
   {:witan/name :input-popn-proj
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-popn-proj
    :witan/params {:src ""}}
   {:witan/name :input-household-popn-proj
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-household-popn
    :witan/params {:src ""}}
   {:witan/name :input-institutional-popn-proj
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-institutional-popn
    :witan/params {:src ""}}
   {:witan/name :input-household-formation-rates
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-household-formation-rates
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
   {:witan/name :adjust-household-popn-proj
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/adjust-hh-popn}
   {:witan/name :adjust-institutional-popn-proj
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/adjust-inst-popn}
   {:witan/name :calculate-household-proportions
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/calc-household-prop}
   {:witan/name :calculate-resident-popn-proj
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/calc-resident-popn}
   {:witan/name :calculate-institutional-proportions
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/calc-inst-prop}
   {:witan/name :adjust-institutional-popn-proj
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/adj-inst-popn}
   {:witan/name :calc-adj-resident-popn
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/adj-resident-popn}
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
   {:witan/name :output-adj-resident-popn
    :witan/version "1.0.0"
    :witan/type :output
    :witan/fn :hh-model/output-resident-popn}
   {:witan/name :output-total-households
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
