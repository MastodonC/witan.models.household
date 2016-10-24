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

(definput get-dclg-household-popn-1-0-0
  {:witan/name :hh-model/get-dclg-household-popn
   :witan/version "1.0.0"
   :witan/key :dclg-household-popn
   :witan/schema sc/HouseholdPopulation})

(definput get-dclg-institutional-popn-1-0-0
  {:witan/name :hh-model/get-dclg-institutional-popn
   :witan/version "1.0.0"
   :witan/key :dclg-institutional-popn
   :witan/schema sc/InstitutionalPopulation})

(definput get-dclg-household-representative-rates-1-0-0
  {:witan/name :hh-model/get-dclg-household-representative-rates
   :witan/version "1.0.0"
   :witan/key :household-representative-rates
   :witan/schema sc/HouseholdRepresentativeRates})

(definput get-dwellings-1-0-0
  {:witan/name :hh-model/get-dwellings
   :witan/version "1.0.0"
   :witan/key :dwellings
   :witan/schema sc/Dwellings})

(definput get-vacancy-dwellings-1-0-0
  {:witan/name :hh-model/get-vacancy-dwellings
   :witan/version "1.0.0"
   :witan/key :vacancy-dwellings
   :witan/schema sc/VacancyDwellings})

;; Functions defining calculations
(defn grp-popn-proj
  "Takes in the CCM population projections.
   Returns the same population grouped by five years bands like DCLG data."
  ;; {:witan/name :hh-model/grp-popn-proj
  ;;  :witan/version "1.0.0"
  ;;  :witan/input-schema {:population sc/PopulationProjections}
  ;;  :witan/output-schema {:banded-projections sc/PopulationProjectionsGrouped}}
  [{:keys [population]} _]
  {:banded-projections (-> population
                           (wds/add-derived-column :age-group
                                                   [:age]
                                                   u/get-age-grp)
                           (wds/rollup :sum :population
                                       [:gss-code :year :sex :age-group]))})

(defn sum-resident-popn-1-0-0
  "Takes in the resident populations.
   Returns the same population summed by household type."
  ;; {:witan/name :hh-model/sum-resident-popn
  ;;  :witan/version "1.0.0"
  ;;  :witan/input-schema {:resident-popn sc/ResidentPopulation}
  ;;  :witan/output-schema {:resident-popn-summed sc/ResidentPopulationSummed}}
  [{:keys [resident-popn]} _]
  {:resident-popn-summed (-> resident-popn
                             (wds/rollup :sum :resident-popn
                                         [:gss-code :year :age-group :sex])
                             (ds/rename-columns {:resident-popn :resident-popn-summed}))})

(defn adjust-resident-proj-1-0-0
  "Takes in the resident population and banded population
   projections. Returns the resident population projections."
  ;; {:witan/name :hh-model/adjust-resident-proj
  ;;  :witan/version "1.0.0"
  ;;  :witan/input-schema {:resident-popn sc/ResidentPopulation
  ;;                       :resident-popn-summed sc/ResidentPopulationSummed
  ;;                       :banded-projections sc/PopulationProjectionsGrouped}
  ;;  :witan/output-schema {:adjusted-resident-popn sc/AdjustedResidentPopulation}}
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

(defworkflowfn apportion-popn-by-relationship-1-0-0
  ""
  {:witan/name :hh-model/apportion-popn-by-relationship
   :witan/version "1.0.0"
   :witan/input-schema {:population sc/PopulationProjections
                        :dclg-household-popn sc/HouseholdPopulation
                        :dclg-institutional-popn sc/InstitutionalPopulation}
   :witan/output-schema {:resident-popn sc/ResidentPopulation}}
  [{:keys [population dclg-household-popn dclg-institutional-popn]} _]
  {:resident-popn {}})

(defworkflowfn calc-institutional-popn-1-0-0
  ""
  {:witan/name :hh-model/calc-institutional-popn
   :witan/version "1.0.0"
   :witan/input-schema {:dclg-institutional-popn sc/InstitutionalPopulation
                        :resident-popn sc/ResidentPopulation}
   :witan/output-schema {:institutional-popn sc/InstitutionalPopulation}}
  [{:keys [dclg-institutional-popn resident-popn]} _]
  {:institutional-popn {}})

(defworkflowfn calc-household-popn-1-0-0
  "Takes in the resident and institutional populations.
   Returns the household population."
  {:witan/name :hh-model/calc-household-popn
   :witan/version "1.0.0"
   :witan/input-schema {:resident-popn sc/AdjustedResidentPopulation
                        :institutional-popn sc/InstitutionalPopulation}
   :witan/output-schema {:household-popn sc/HouseholdPopulation}}
  [{:keys [resident-popn institutional-popn]} _]
  {:household-popn (-> resident-popn
                       (wds/join institutional-popn
                                 [:gss-code :age-group :year :sex :relationship])
                       (wds/add-derived-column :household-popn
                                               [:resident-popn :institutional-popn] -)
                       (ds/select-columns [:gss-code :age-group :sex :year
                                           :relationship :household-popn]))})

(defworkflowfn calc-households-1-0-0
  "Takes in household rates and grouped household population.
  Returns the number of households."
  {:witan/name :hh-model/calc-households
   :witan/version "1.0.0"
   :witan/input-schema {:household-representative-rates sc/HouseholdRepresentativeRates
                        :household-popn sc/HouseholdPopulation}
   :witan/output-schema {:households sc/Households
                         :total-households sc/TotalHouseholds}}
  [{:keys [household-representative-rates household-popn]} _]
  (let [households (-> household-representative-rates
                       (wds/join household-popn
                                 [:gss-code :year :sex :relationship :age-group])
                       (wds/add-derived-column :households
                                               [:household-popn :hh-repr-rates] *)
                       (ds/select-columns [:gss-code :year :sex :relationship
                                           :age-group :households]))]
    {:households households
     :total-households (wds/rollup households :sum :households [:gss-code :year])}))

(defworkflowfn convert-to-dwellings-1-0-0
  "Takes in the total households and the dwellings, vacancy dwellings and second home rates.
  Returns the number of dwellings."
  {:witan/name :hh-model/convert-to-dwellings
   :witan/version "1.0.0"
   :witan/input-schema {:total-households sc/TotalHouseholds
                        :dclg-dwellings sc/DclgDwellings
                        :vacancy-dwellings sc/VacancyDwellings}
   :witan/param-schema {:second-home-rate java.lang.Double}
   :witan/output-schema {:dwellings sc/Dwellings}}
  [{:keys [total-households dclg-dwellings vacancy-dwellings]} {:keys [second-home-rate]}]
  {:dwellings {}
   ;; (-> total-households
   ;;                   (wds/join occupancy-rates [:gss-code :year])
   ;;                   (wds/add-derived-column :dwellings
   ;;                                           [:households :occupancy-rates]
   ;;                                           (fn [hh occ] (* hh (- 1 occ))))
   ;;                   (ds/select-columns [:gss-code :year :dwellings]))
   })

;; Functions to handle the model outputs
(defworkflowoutput output-households-1-0-0
  "Returns the households"
  {:witan/name :hh-model/output-households
   :witan/version "1.0.0"
   :witan/input-schema {:households sc/Households}}
  [{:keys [households]} _]
  {:households households})

(defworkflowoutput output-total-households-1-0-0
  "Returns the total households"
  {:witan/name :hh-model/output-total-households
   :witan/version "1.0.0"
   :witan/input-schema {:total-households sc/TotalHouseholds}}
  [{:keys [total-households]} _]
  {:total-households total-households})

(defworkflowoutput output-dwellings-1-0-0
  "Returns the dwellings"
  {:witan/name :hh-model/output-dwellings
   :witan/version "1.0.0"
   :witan/input-schema {:dwellings sc/Dwellings}}
  [{:keys [dwellings]} _]
  {:dwellings dwellings})
