(ns ^{:doc "Defines the functions for each step of the household model.
            The functions are defined using macros that make it easy to
            be packaged into a datastructure to be run by a workspace executor"}
 witan.models.household
  (:require [witan.workspace-api :refer [defworkflowfn definput defworkflowoutput]]
            [witan.models.schemas :as sc]
            [clojure.core.matrix.dataset :as ds]
            [witan.datasets :as wds]
            [witan.models.utils :as u]))

;; Functions to retrieve the five datasets needed
(definput get-popn-proj-1-0-0
  {:witan/name :hh-model/get-popn-proj
   :witan/version "1.0.0"
   :witan/key :population
   :witan/schema sc/PopulationProjections})

(definput get-resident-popn-1-0-0
  {:witan/name :hh-model/get-resident-popn
   :witan/version "1.0.0"
   :witan/key :resident-popn
   :witan/schema sc/ResidentPopulation})

(definput get-institutional-popn-1-0-0
  {:witan/name :hh-model/get-institutional-popn
   :witan/version "1.0.0"
   :witan/key :institutional-popn
   :witan/schema sc/InstitutionalPopulation})

(definput get-household-representative-rates-1-0-0
  {:witan/name :hh-model/get-household-representative-rates
   :witan/version "1.0.0"
   :witan/key :household-representative-rates
   :witan/schema sc/HouseholdRepresentativeRates})

(definput get-vacancy-rates-1-0-0
  {:witan/name :hh-model/get-vacancy-rates
   :witan/version "1.0.0"
   :witan/key :vacancy-rates
   :witan/schema sc/VacancyRates})

(definput get-second-homes-rates-1-0-0
  {:witan/name :hh-model/get-second-homes-rates
   :witan/version "1.0.0"
   :witan/key :second-homes-rates
   :witan/schema sc/SecondHomesRates})

;; Functions defining calculations
(defworkflowfn grp-popn-proj-1-0-0
  "Takes in the CCM population projections.
   Returns the same population grouped by five years bands like DCLG data."
  {:witan/name :hh-model/grp-popn-proj
   :witan/version "1.0.0"
   :witan/input-schema {:population sc/PopulationProjections}
   :witan/output-schema {:banded-projections sc/PopulationProjectionsGrouped}}
  [{:keys [population]} _]
  {:banded-projections (-> population
                           (wds/add-derived-column :age-group
                                                   [:age]
                                                   u/get-age-grp)
                           (wds/rollup :sum :population
                                       [:gss-code :year :sex :age-group]))})

(defworkflowfn sum-resident-popn-1-0-0
  "Takes in the resident populations.
   Returns the same population summed by household type."
  {:witan/name :hh-model/sum-resident-popn
   :witan/version "1.0.0"
   :witan/input-schema {:resident-popn sc/ResidentPopulation}
   :witan/output-schema {:resident-popn-summed sc/ResidentPopulationSummed}}
  [{:keys [resident-popn]} _]
  {:resident-popn-summed (-> resident-popn
                             (wds/rollup :sum :resident-popn
                                         [:gss-code :year :age-group :sex])
                             (ds/rename-columns {:resident-popn :resident-popn-summed}))})

(defworkflowfn adjust-resident-proj-1-0-0
  "Takes in the resident population and banded population
   projections. Returns the resident population projections."
  {:witan/name :hh-model/adjust-resident-proj
   :witan/version "1.0.0"
   :witan/input-schema {:resident-popn sc/ResidentPopulation
                        :resident-popn-summed sc/ResidentPopulationSummed
                        :banded-projections sc/PopulationProjectionsGrouped}
   :witan/output-schema {:adjusted-resident-popn sc/AdjustedResidentPopulation}}
  [{:keys [resident-popn resident-popn-summed banded-projections]} _]
  (let [joined-resident-popn (wds/join banded-projections resident-popn
                                       [:gss-code :year :sex :age-group])
        joined-summed-popn (wds/join resident-popn-summed joined-resident-popn
                                     [:gss-code :year :sex :age-group])]
    {:adjusted-resident-popn (-> joined-summed-popn
                                      (wds/add-derived-column :adjusted-resident-popn
                                                              [:resident-popn-summed :resident-popn
                                                               :population]
                                                              (fn [sum res popn]
                                                                (* popn (wds/safe-divide
                                                                         [res sum]))))
                                      (ds/select-columns [:gss-code :age-group :sex
                                                          :year :relationship
                                                          :adjusted-resident-popn]))}))

(defworkflowfn calc-household-popn-1-0-0
  "Takes in the resident and institutional populations.
   Returns the household population."
  {:witan/name :hh-model/calc-household-popn
   :witan/version "1.0.0"
   :witan/input-schema {:adjusted-resident-popn sc/AdjustedResidentPopulation
                        :institutional-popn sc/InstitutionalPopulation}
   :witan/output-schema {:household-popn sc/HouseholdPopulation}}
  [{:keys [adjusted-resident-popn institutional-popn]} _]
  {:household-popn (-> adjusted-resident-popn
                       (wds/join institutional-popn
                                 [:gss-code :age-group :year :sex :relationship])
                       (wds/add-derived-column :household-popn
                                               [:adjusted-resident-popn :institutional-popn] -)
                       (ds/select-columns [:gss-code :age-group :sex :year
                                           :relationship :household-popn]))})

(defworkflowfn calc-households-1-0-0
  "Takes in household rates and grouped household population.
  Returns the number of households."
  {:witan/name :hh-model/calc-households
   :witan/version "1.0.0"
   :witan/input-schema {:household-representative-rates sc/HouseholdRepresentativeRates
                        :household-popn sc/HouseholdPopulation}
   :witan/output-schema {:households sc/Households}}
  [{:keys [household-representative-rates household-popn]} _]
  {:households (-> household-representative-rates
                   (wds/join household-popn
                             [:gss-code :year :sex :relationship :age-group])
                   (wds/add-derived-column :households
                                           [:household-popn :hh-repr-rates] *)
                   (ds/select-columns [:gss-code :year :sex :relationship
                                       :age-group :households]))})

(defworkflowfn calc-total-households-1-0-0
  "Takes in the households.
  Returns the total number of households."
  {:witan/name :hh-model/calc-total-households
   :witan/version "1.0.0"
   :witan/input-schema {:households sc/Households}
   :witan/output-schema {:total-households sc/TotalHouseholds}}
  [{:keys [households]} _]
  {:total-households (wds/rollup households :sum :households [:gss-code :year])})

(defworkflowfn calc-occupancy-rates-1-0-0
  "Takes in the vacancy rates and second homes rates.
  Returns the occupancy rates."
  {:witan/name :hh-model/calc-occupancy-rates
   :witan/version "1.0.0"
   :witan/input-schema {:vacancy-rates sc/VacancyRates
                        :second-homes-rates sc/SecondHomesRates}
   :witan/output-schema {:occupancy-rates sc/OccupancyRates}}
  [{:keys [vacancy-rates second-homes-rates]} _]
  {:occupancy-rates (-> vacancy-rates
                        (wds/join second-homes-rates [:gss-code :year])
                        (wds/add-derived-column :occupancy-rates
                                                [:vacancy-rates :second-homes-rates] +)
                        (ds/select-columns [:gss-code :year :occupancy-rates]))})

(defworkflowfn calc-dwellings-1-0-0
  "Takes in the total households and the occupancy rate.
  Returns the number of dwellings."
  {:witan/name :hh-model/calc-dwellings
   :witan/version "1.0.0"
   :witan/input-schema {:total-households sc/TotalHouseholds
                        :occupancy-rates sc/OccupancyRates}
   :witan/output-schema {:dwellings sc/Dwellings}}
  [{:keys [total-households occupancy-rates]} _]
  {:dwellings (-> total-households
                  (wds/join occupancy-rates [:gss-code :year])
                  (wds/add-derived-column :dwellings
                                          [:households :occupancy-rates]
                                          (fn [hh occ] (* hh (- 1 occ))))
                  (ds/select-columns [:gss-code :year :dwellings]))})

;; Functions to handle the model outputs
(defworkflowoutput output-households-1-0-0
  "Returns the total households and the dwellings"
  {:witan/name :hh-model/output-households
   :witan/version "1.0.0"
   :witan/input-schema {:total-households sc/TotalHouseholds}}
  [{:keys [total-households]} _]
  {:total-households total-households})

(defworkflowoutput output-dwellings-1-0-0
  "Returns the total households and the dwellings"
  {:witan/name :hh-model/output-dwellings
   :witan/version "1.0.0"
   :witan/input-schema {:dwellings sc/Dwellings}}
  [{:keys [dwellings]} _]
  {:dwellings dwellings})
