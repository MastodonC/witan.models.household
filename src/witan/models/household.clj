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
(defworkflowfn calc-household-popn-1-0-0
  "Takes in the resident and institutional populations.
   Returns the household population."
  {:witan/name :hh-model/calc-household-popn
   :witan/version "1.0.0"
   :witan/input-schema {:resident-popn s/ResidentPopulation
                        :institutional-popn s/InstitutionalPopulation}
   :witan/output-schema {:household-popn s/HouseholdPopulation}}
  [{:keys [resident-popn institutional-popn]} _]
  (let [joined-popns (wds/join resident-popn institutional-popn
                               [:gss-code :age :year :sex :relationship])
        intermed-ds (wds/add-derived-column joined-popns :household-popn
                                            [:resident-popn :institutional-popn]
                                            -)
        household-popn (ds/select-columns intermed-ds
                                          [:gss-code :age :sex :year
                                           :relationship :household-popn])]
    {:household-popn household-popn}))

(defworkflowfn grp-household-popn-1-0-0
  "Takes in the household population. Returns the household popn
   grouped by five years bands like DCLG data."
  {:witan/name :hh-model/grp-household-popn
   :witan/version "1.0.0"
   :witan/input-schema {:household-popn s/HouseholdPopulation}
   :witan/output-schema {:household-popn-grp s/HouseholdPopulationGrouped}}
  [{:keys [household-popn]} _]
  (let [popn-with-grp-col (wds/add-derived-column household-popn :age-group
                                                  [:age]
                                                  (fn [a] (u/get-age-grp a)))
        household-popn-gouped (wds/rollup popn-with-grp-col :sum :household-popn
                                          [:gss-code :year :sex :relationship :age-group])]
    {:household-popn-grp household-popn-gouped}))

(defworkflowfn calc-households-1-0-0
  "Takes in household rates and grouped household population.
  Returns the number of households."
  {:witan/name :hh-model/calc-households
   :witan/version "1.0.0"
   :witan/input-schema {:household-representative-rates s/HouseholdRepresentativeRates
                        :household-popn-grp s/HouseholdPopulationGrouped}
   :witan/output-schema {:households s/Households}}
  [{:keys [household-representative-rates household-popn-grp]} _]
  (let [joined-ds (wds/join household-representative-rates household-popn-grp
                            [:gss-code :year :sex :relationship :age-group])
        households-calc (wds/add-derived-column joined-ds :households
                                                [:household-popn :hh-repr-rates]
                                                *)
        households (ds/select-columns households-calc
                                      [:gss-code :year :sex :relationship
                                       :age-group :households])]
    {:households households}))

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
  (let [joined-ds (wds/join vacancy-rates second-homes-rates [:gss-code :year])
        occupancy-calc (wds/add-derived-column joined-ds :occupancy-rates
                                               [:vacancy-rates :second-homes-rates] +)
        occupancy-ds (ds/select-columns occupancy-calc [:gss-code :year :occupancy-rates])]
    {:occupancy-rates occupancy-ds}))

(defworkflowfn calc-dwellings-1-0-0
  "Takes in the total households and the occupancy rate.
  Returns the number of dwellings."
  {:witan/name :hh-model/calc-dwellings
   :witan/version "1.0.0"
   :witan/input-schema {:total-households s/TotalHouseholds
                        :occupancy-rates s/OccupancyRates}
   :witan/output-schema {:dwellings s/Dwellings}}
  [{:keys [total-households occupancy-rates]} _]
  (let [joined-ds (wds/join total-households occupancy-rates [:gss-code :year])
        dwellings-calc (wds/add-derived-column joined-ds :dwellings
                                               [:households :occupancy-rates]
                                               (fn [hh or] (* hh (- 1 or))))
        dwellings-ds (ds/select-columns dwellings-calc [:gss-code :year :dwellings])]
    {:dwellings dwellings-ds}))

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
