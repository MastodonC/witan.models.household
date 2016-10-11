(ns ^{:doc "Defines the functions for each step of the household model.
            The functions are defined using macros that make it easy to
            be packaged into a datastructure to be run by a workspace executor"}
 witan.models.household
  (:require [witan.workspace-api :refer [defworkflowfn definput defworkflowoutput]]
            [witan.models.schemas :as s]
            [clojure.core.matrix.dataset :as ds]
            [witan.datasets :as wds]
            [schema.core :as sc]
            [witan.models.utils :as u]))

;; Functions to retrieve the five datasets needed
(definput get-popn-proj-1-0-0
  {:witan/name :hh-model/get-popn-proj
   :witan/version "1.0.0"
   :witan/key :population
   :witan/schema s/PopulationProjections})

(definput get-resident-popn-1-0-0
  {:witan/name :hh-model/get-resident-popn
   :witan/version "1.0.0"
   :witan/key :resident-popn
   :witan/schema s/ResidentPopulation})

(definput get-institutional-popn-1-0-0
  {:witan/name :hh-model/get-institutional-popn
   :witan/version "1.0.0"
   :witan/key :institutional-popn
   :witan/schema s/InstitutionalPopulation})

(definput get-household-representative-rates-1-0-0
  {:witan/name :hh-model/get-household-representative-rates
   :witan/version "1.0.0"
   :witan/key :household-representative-rates
   :witan/schema s/HouseholdRepresentativeRates})

(definput get-vacancy-rates-1-0-0
  {:witan/name :hh-model/get-vacancy-rates
   :witan/version "1.0.0"
   :witan/key :vacancy-rates
   :witan/schema s/VacancyRates})

(definput get-second-homes-rates-1-0-0
  {:witan/name :hh-model/get-second-homes-rates
   :witan/version "1.0.0"
   :witan/key :second-homes-rates
   :witan/schema s/SecondHomesRates})

;; Functions defining calculations
(defworkflowfn grp-popn-proj-1-0-0
  "Takes in the CCM population projections.
   Returns the same population grouped by five years bands like DCLG data."
  {:witan/name :hh-model/grp-popn-proj
   :witan/version "1.0.0"
   :witan/input-schema {:population s/PopulationProjections}
   :witan/output-schema {:banded-projections s/PopulationProjectionsGrouped}}
  [{:keys [population]} _]
  {:banded-projections (-> population
                           (wds/add-derived-column :age-group
                                                   [:age]
                                                   u/get-age-grp)
                           (wds/rollup :sum :household-popn
                                       [:gss-code :year :sex :relationship :age-group]))})

(defworkflowfn sum-resident-popn-1-0-0
  "Takes in the resident populations.
   Returns the same population summed by household type."
  {:witan/name :hh-model/sum-resident-popn
   :witan/version "1.0.0"
   :witan/input-schema {:resident-popn s/ResidentPopulation}
   :witan/output-schema {:resident-popn-summed s/ResidentPopulationSummed}}
  [{:keys [resident-popn]} _]
  {:resident-popn-summed (-> resident-popn
                             (wds/rollup :sum :resident-popn-summed
                                         [:gss-code :year :sex :relationship :age-group]))})

(defworkflowfn adjust-resident-proj-1-0-0
  "Takes in the resident population and banded population
   projections. Returns the resident population projections."
  {:witan/name :hh-model/adjust-resident-proj
   :witan/version "1.0.0"
   :witan/input-schema {:resident-popn-summed s/ResidentPopulationSummed
                        :banded-projections s/PopulationProjectionsGrouped}
   :witan/output-schema {:adjusted-resident-popn-proj s/AdjustedResidentPopulation}}
  [{:keys [resident-popn-summed banded-projections]} _]
  {:adjusted-resident-popn-proj (-> resident-popn-summed
                                    (wds/join banded-projections
                                              [:gss-code :age :year :sex :relationship])
                                    (wds/add-derived-column :adjusted-resident-popn-proj
                                                            [:resident-popn-summed :resident-popn
                                                             :population]
                                                            +)
                                    (ds/select-columns [:gss-code :age :sex :year
                                                        :relationship :household-popn]))})

(defworkflowfn calc-household-popn-1-0-0
  "Takes in the resident and institutional populations.
   Returns the household population."
  {:witan/name :hh-model/calc-household-popn
   :witan/version "1.0.0"
   :witan/input-schema {:resident-popn s/ResidentPopulation
                        :institutional-popn s/InstitutionalPopulation}
   :witan/output-schema {:household-popn s/HouseholdPopulation}}
  [{:keys [resident-popn institutional-popn]} _]
  {:household-popn (-> resident-popn
                       (wds/join institutional-popn
                                 [:gss-code :age :year :sex :relationship])
                       (wds/add-derived-column :household-popn
                                               [:resident-popn :institutional-popn] -)
                       (ds/select-columns [:gss-code :age :sex :year
                                           :relationship :household-popn]))})

(defworkflowfn calc-households-1-0-0
  "Takes in household rates and grouped household population.
  Returns the number of households."
  {:witan/name :hh-model/calc-households
   :witan/version "1.0.0"
   :witan/input-schema {:household-representative-rates s/HouseholdRepresentativeRates
                        :household-popn-grp s/HouseholdPopulationGrouped}
   :witan/output-schema {:households s/Households}}
  [{:keys [household-representative-rates household-popn-grp]} _]
  {:households (-> household-representative-rates
                   (wds/join household-popn-grp
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
   :witan/input-schema {:households s/Households}
   :witan/output-schema {:total-households s/TotalHouseholds}}
  [{:keys [households]} _]
  {:total-households (wds/rollup households :sum :households [:gss-code :year])})

(defworkflowfn calc-occupancy-rates-1-0-0
  "Takes in the vacancy rates and second homes rates.
  Returns the occupancy rates."
  {:witan/name :hh-model/calc-occupancy-rates
   :witan/version "1.0.0"
   :witan/input-schema {:vacancy-rates s/VacancyRates
                        :second-homes-rates s/SecondHomesRates}
   :witan/output-schema {:occupancy-rates s/OccupancyRates}}
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
   :witan/input-schema {:total-households s/TotalHouseholds
                        :occupancy-rates s/OccupancyRates}
   :witan/output-schema {:dwellings s/Dwellings}}
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
   :witan/input-schema {:total-households s/TotalHouseholds}}
  [{:keys [total-households]} _]
  {:total-households total-households})

(defworkflowoutput output-dwellings-1-0-0
  "Returns the total households and the dwellings"
  {:witan/name :hh-model/output-dwellings
   :witan/version "1.0.0"
   :witan/input-schema {:dwellings s/Dwellings}}
  [{:keys [dwellings]} _]
  {:dwellings dwellings})
