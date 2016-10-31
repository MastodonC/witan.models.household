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
(definput population-1-0-0
  {:witan/name :hh-model/population
   :witan/version "1.0.0"
   :witan/key :population
   :witan/schema sc/PopulationProjections})

(definput dclg-household-popn-1-0-0
  {:witan/name :hh-model/dclg-household-popn
   :witan/version "1.0.0"
   :witan/key :dclg-household-popn
   :witan/schema sc/HouseholdPopulation})

(definput dclg-institutional-popn-1-0-0
  {:witan/name :hh-model/dclg-institutional-popn
   :witan/version "1.0.0"
   :witan/key :dclg-institutional-popn
   :witan/schema sc/DclgInstitutionalPopulation})

(definput dclg-household-representative-rates-1-0-0
  {:witan/name :hh-model/dclg-household-representative-rates
   :witan/version "1.0.0"
   :witan/key :dclg-household-representative-rates
   :witan/schema sc/HouseholdRepresentativeRates})

(definput dclg-dwellings-1-0-0
  {:witan/name :hh-model/dclg-dwellings
   :witan/version "1.0.0"
   :witan/key :dclg-dwellings
   :witan/schema sc/Dwellings})

(definput vacancy-dwellings-1-0-0
  {:witan/name :hh-model/vacancy-dwellings
   :witan/version "1.0.0"
   :witan/key :vacancy-dwellings
   :witan/schema sc/VacancyDwellings})

;; Functions defining calculations
(defn grp-popn-proj
  "Takes in the CCM population projections.
   Returns the same population grouped by five years bands like DCLG data."
  [population]
  (-> population
      (wds/add-derived-column :age-group
                              [:age]
                              u/get-age-grp)
      (wds/rollup :sum :population
                  [:gss-code :year :sex :age-group])))

(defn create-resident-popn
  "Takes in dclg household and institutional populations.
   Outputs a dclg resident population"
  [dclg-hh-popn dclg-inst-popn]
  (-> dclg-hh-popn
      (wds/join dclg-inst-popn
                [:gss-code :age-group :sex :year :relationship])
      (wds/add-derived-column :dclg-resident-popn
                              [:dclg-institutional-popn :household-popn] +)
      (ds/select-columns [:gss-code :age-group :sex :year :relationship :dclg-resident-popn])))

(defn sum-resident-popn
  "Takes in the resident populations.
   Returns the same population summed by relationship."
  [resident-popn]
  (-> resident-popn
      (wds/rollup :sum :dclg-resident-popn
                  [:gss-code :year :age-group :sex])
      (ds/rename-columns {:dclg-resident-popn :resident-popn-summed})))

(defn calc-resident-proj
  "Takes in the dclg resident population, dclg resident population summed by relationship
   and banded population projections. Returns the resident population projections."
  [resident-popn resident-popn-summed banded-projections]
  (let [joined-resident-popn (wds/join banded-projections resident-popn
                                       [:gss-code :year :sex :age-group])
        joined-summed-popn (wds/join resident-popn-summed joined-resident-popn
                                     [:gss-code :year :sex :age-group])]
    (-> joined-summed-popn
        (wds/add-derived-column :resident-popn
                                [:resident-popn-summed :dclg-resident-popn
                                 :population]
                                (fn [sum res popn]
                                  (* popn (wds/safe-divide
                                           [res sum]))))
        (ds/select-columns [:gss-code :age-group :sex
                            :year :relationship
                            :resident-popn]))))

(defworkflowfn apportion-popn-by-relationship-1-0-0
  "Takes in a population (output of CCM), dclg household and institutional
   populations. Returns a resident population with relationships and grouped
   by the same age bands as the dclg."
  {:witan/name :hh-model/apportion-popn-by-relationship
   :witan/version "1.0.0"
   :witan/input-schema {:population sc/PopulationProjections
                        :dclg-household-popn sc/HouseholdPopulation
                        :dclg-institutional-popn sc/DclgInstitutionalPopulation}
   :witan/output-schema {:resident-popn sc/ResidentPopulation
                         :dclg-resident-popn sc/DclgResidentPopulation}}
  [{:keys [population dclg-household-popn dclg-institutional-popn]} _]
  (let [popn-by-age-bands (grp-popn-proj population)
        dclg-resident-popn (create-resident-popn dclg-household-popn
                                                 dclg-institutional-popn)
        dclg-resident-summed (sum-resident-popn dclg-resident-popn)]
    {:resident-popn (calc-resident-proj dclg-resident-popn
                                        dclg-resident-summed
                                        popn-by-age-bands)
     :dclg-resident-popn dclg-resident-popn}))

(defworkflowfn calc-institutional-popn-1-0-0
  "Takes in dclg institutional population and witan resident population.
   Outputs witan institutional population."
  {:witan/name :hh-model/calc-institutional-popn
   :witan/version "1.0.0"
   :witan/input-schema {:dclg-institutional-popn sc/DclgInstitutionalPopulation
                        :resident-popn sc/ResidentPopulation
                        :dclg-resident-popn sc/DclgResidentPopulation}
   :witan/output-schema {:institutional-popn sc/InstitutionalPopulation}}
  [{:keys [dclg-institutional-popn dclg-resident-popn resident-popn]} _]
  {:institutional-popn (-> dclg-institutional-popn
                           (wds/join dclg-resident-popn
                                     [:gss-code :year :sex :age-group :relationship])
                           (wds/join resident-popn
                                     [:gss-code :year :sex :age-group :relationship])
                           (wds/add-derived-column :institutional-popn
                                                   [:age-group
                                                    :resident-popn :dclg-resident-popn
                                                    :dclg-institutional-popn]
                                                   (fn [age res dres dinst]
                                                     (if (some #(= age %)
                                                               [:75_79 :80_84 :85&])
                                                       (* (/ dinst dres) res) dinst)))
                           (ds/select-columns [:gss-code :age-group :sex :year
                                               :relationship :institutional-popn]))})

(defworkflowfn calc-household-popn-1-0-0
  "Takes in the resident and institutional populations.
   Returns the household population."
  {:witan/name :hh-model/calc-household-popn
   :witan/version "1.0.0"
   :witan/input-schema {:resident-popn sc/ResidentPopulation
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
  "Takes in household formation rates and grouped household population.
  Returns the number of households."
  {:witan/name :hh-model/calc-households
   :witan/version "1.0.0"
   :witan/input-schema {:dclg-household-representative-rates sc/HouseholdRepresentativeRates
                        :household-popn sc/HouseholdPopulation}
   :witan/output-schema {:households sc/Households
                         :total-households sc/TotalHouseholds}}
  [{:keys [dclg-household-representative-rates household-popn]} _]
  (let [households (-> dclg-household-representative-rates
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
                        :dclg-dwellings sc/Dwellings
                        :vacancy-dwellings sc/VacancyDwellings}
   :witan/param-schema {:second-home-rate java.lang.Double}
   :witan/output-schema {:dwellings sc/Dwellings}}
  [{:keys [total-households dclg-dwellings vacancy-dwellings]} {:keys [second-home-rate]}]
  (let [vacant-dwellings (wds/subset-ds (wds/select-from-ds vacancy-dwellings
                                                            {:year {:eq (u/get-last-year
                                                                         vacancy-dwellings)}})
                                        :cols :vacancy-dwellings)
        last-year-dwellings (wds/subset-ds (wds/select-from-ds dclg-dwellings
                                                               {:year {:eq (u/get-last-year
                                                                            dclg-dwellings)}})
                                           :cols :dwellings)
        vacancy-rates (ds/dataset {:gss-code (u/make-coll (wds/subset-ds total-households
                                                                         :cols :gss-code))
                                   :year (u/make-coll (wds/subset-ds total-households :cols :year))
                                   :vacancy-rates (repeat (count
                                                           (u/make-coll
                                                            (wds/subset-ds total-households
                                                                           :cols :year)))
                                                          (/ vacant-dwellings last-year-dwellings))})
        second-home-rates (ds/dataset {:gss-code (u/make-coll
                                                  (wds/subset-ds total-households :cols :gss-code))
                                       :year (u/make-coll
                                              (wds/subset-ds total-households :cols :year))
                                       :second-home-rates (repeat (count
                                                                   (u/make-coll
                                                                    (wds/subset-ds total-households
                                                                                   :cols :year)))
                                                                  second-home-rate)})]
    {:dwellings
     (ds/join-rows dclg-dwellings
                   (-> vacancy-rates
                       (wds/join second-home-rates [:gss-code :year])
                       (wds/join total-households [:gss-code :year])
                       (wds/add-derived-column :dwellings
                                               [:households :second-home-rates :vacancy-rates]
                                               (fn [hh shr vr] (/ hh (- (- 1 vr) shr))))
                       (ds/select-columns [:gss-code :year :dwellings])
                       (wds/select-from-ds {:year {:gt (u/get-last-year dclg-dwellings)}})))}))

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
