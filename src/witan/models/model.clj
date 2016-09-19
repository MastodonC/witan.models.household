(ns witan.models.model
  (:require [witan.workspace-api :refer [defmodel
                                         definput]]
            [witan.workspace-api.protocols :as p]
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
   [:calculate-dwellings :output-dwellings]])

(def hh-model-catalog
  [;; Input functions
   {:witan/name :input-resident-popn
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh/get-resident-popn}
   {:witan/name :input-institutional-popn
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh/get-institutional-popn}
   {:witan/name :input-household-rates
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh/get-household-rates}
   {:witan/name :input-vacancy-rates
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh/get-vacancy-rates}
   {:witan/name :input-second-homes-rates
    :witan/version "1.0.0"
    :witan/type :input
    :witan/fn :hh/get-second-homes-rates}
   ;; Calculation functions
   {:witan/name :calculate-household-popn
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh/calc-household-popn}
   {:witan/name :group-household-popn
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh/grp-household-popn}
   {:witan/name :calculate-households
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh/calc-households}
   {:witan/name :calculate-total-households
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh/calc-total-households}
   {:witan/name :calculate-occupancy-rate
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh/calc-occupancy-rate}
   {:witan/name :calculate-dwellings
    :witan/version "1.0.0"
    :witan/type :function
    :witan/fn :hh/calc-dwellings}
   ;; Output function
   {:witan/name :output-dwellings
    :witan/version "1.0.0"
    :witan/type :output
    :witan/fn :hh/output-dwellings}])

;; (defmodel household-model
;;   "The household model"
;;   {:witan/name :hh-model/household-model
;;    :witan/version "1.0.0"}
;;   {:workflow hh-model-workflow
;;    :catalog hh-model-catalog})
