(ns witan.models.model
  (:require [witan.workspace-api :refer [defmodel]]
            [witan.workspace-api.protocols :as p]
            [witan.workspace-api.utils :refer [map-fn-meta
                                               map-model-meta]]
            [witan.models.household :as hh]))

(def hh-model-workflow
  [;; Household population
   [:input-resident-popn :calculate-household-popn]
   [:input-institutional-popn :calculate-household-popn]
   [:calculate-household-popn :group-household-popn]

   ;; Households
   [:input-household-rates :calculate-households]
   [:group-household-popn :calculate-households]
   [:calculate-households :calculate-total-households]

   ;; Household occupancy
   [:input-vacancy-rates :calculate-occupancy-rate]
   [:input-second-homes-rates :calculate-occupancy-rate]
   [:calculate-total-households :calculate-dwellings]
   [:calculate-occupancy-rate :calculate-dwellings]
   [:calculate-total-households :gather-outputs]
   [:calculate-dwellings :gather-outputs]
   [:gather-outputs :final-outputs]])

(def hh-model-catalog
  [;; Input functions
   {:witan/name :input-resident-popn
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-resident-popn}
   {:witan/name :input-institutional-popn
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-institutional-popn}
   {:witan/name :input-household-rates
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-household-rates}
   {:witan/name :input-vacancy-rates
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-vacancy-rates}
   {:witan/name :input-second-homes-rates
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh-model/get-second-homes-rates}
   ;; Calculation functions
   {:witan/name :calculate-household-popn
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/calc-household-popn}
   {:witan/name :group-household-popn
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/grp-household-popn}
   {:witan/name :calculate-households
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/calc-households}
   {:witan/name :calculate-total-households
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/calc-total-households}
   {:witan/name :calculate-occupancy-rate
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/calc-occupancy-rate}
   {:witan/name :calculate-dwellings
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/calc-dwellings}
   ;; Outputs
   {:witan/name :gather-outputs
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh-model/gather-outputs}
   {:witan/name :final-outputs
    :witan/version "1.0.0"
    :witan/type :output
    :witan/fn :hh-model/final-outputs}])

(defmodel household-model
  "The household model"
  {:witan/name :hh-model/household-model
   :witan/version "1.0.0"}
  {:workflow hh-model-workflow
   :catalog hh-model-catalog})

(defn model-library
  []
  (reify p/IModelLibrary
    (available-fns [_]
      (map-fn-meta
       hh/get-resident-popn-1-0-0
       hh/get-institutional-popn-1-0-0
       hh/get-household-rates-1-0-0
       hh/get-vacancy-rates-1-0-0
       hh/get-second-homes-rates-1-0-0
       hh/calc-household-popn-1-0-0
       hh/grp-household-popn-1-0-0
       hh/calc-households-1-0-0
       hh/calc-total-households-1-0-0
       hh/calc-occupancy-rate-1-0-0
       hh/calc-dwellings-1-0-0
       hh/gather-outputs-1-0-0
       hh/final-outputs-1-0-0))
    (available-models [_]
      (map-model-meta household-model))))
