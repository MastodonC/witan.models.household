(ns witan.models.acceptance.workspace-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [witan.models.schemas :as sc]
            [witan.models.household :refer :all]
            [witan.models.model :as m]
            [witan.workspace-api.protocols :as p]
            [witan.workspace-executor.core :as wex]
            [clojure.core.matrix.dataset :as ds]
            [witan.datasets :as wds]
            [witan.models.test-utils :as tu]))

;; Testing the model can be run by the workspace executor

;; Run test on test_datasets:
(def test-data
  {:population ["data/test_datasets/gla_test_popn_barnet.csv" sc/PopulationProjections]
   :dclg-household-popn ["data/test_datasets/dclg_hh_popn_barnet.csv" sc/DclgHouseholdPopulation]
   :dclg-institutional-popn ["data/test_datasets/dclg_inst_popn_barnet.csv"
                             sc/DclgInstitutionalPopulation]
   :dclg-household-representative-rates ["data/test_datasets/dclg_hh_repr_rates_barnet.csv"
                                         sc/HouseholdRepresentativeRates]
   :dclg-dwellings ["data/test_datasets/dclg_dwellings_barnet.csv" sc/Dwellings]
   :vacancy-dwellings ["data/test_datasets/dclg_vacant_dwellings_barnet.csv" sc/VacancyDwellings]
   :gla-total-households ["data/test_datasets/gla_test_totalhh_barnet.csv" sc/TotalHouseholds]})

(defn add-input-params
  [input]
  (assoc-in input [:witan/params :fn] (partial tu/read-inputs test-data input)))

;; Run test on default_datasets:
(def gss-code "E09000003")

(defn with-gss
  [id]
  (str id "_" gss-code ".csv"))

(def local-inputs
  {:population [(with-gss "./data/default_datasets/population/ons_2014_based_snpp")
                sc/PopulationProjections]
   :dclg-household-popn
   [(with-gss "./data/default_datasets/household_population/dclg_2014_hh_popn_proj")
    sc/DclgHouseholdPopulation]
   :dclg-institutional-popn
   [(with-gss "./data/default_datasets/institutional_population/dclg_2014_inst_popn_proj")
    sc/DclgInstitutionalPopulation]
   :dclg-household-representative-rates
   [(with-gss "./data/default_datasets/household_representative_rates/dclg_2014_hh_repr_rates")
    sc/HouseholdRepresentativeRates]
   :dclg-dwellings [(with-gss "./data/default_datasets/dwellings/dclg_2015_dwellings")
                    sc/Dwellings]
   :vacancy-dwellings
   [(with-gss "./data/default_datasets/vacancy_dwellings/dclg_2015_vacant_dwellings")
    sc/VacancyDwellings]})

(defn add-params-to-local-input
  [input]
  (assoc-in input [:witan/params :fn] (partial tu/read-inputs local-inputs input)))

;; Tests:
(deftest household-workspace-test
  (testing "The model is run on the workspace and returns the outputs expected"
    (let [fixed-catalog (mapv #(if (= (:witan/type %) :input)
                                 (add-input-params %) %)
                              (:catalog m/household-model))
          workspace     {:workflow  (:workflow m/household-model)
                         :catalog   fixed-catalog
                         :contracts (p/available-fns (m/model-library))}
          workspace'    (s/with-fn-validation (wex/build! workspace))
          result        (apply merge (wex/run!! workspace' {}))

          gla-total-hh (wds/select-from-ds
                        (tu/read-inputs test-data
                                        {:witan/name :gla-total-households} [] [])
                        {:year {:lt 2040}})
          total-hh (:total-households result)

          joined-ds (wds/join total-hh
                              (ds/rename-columns gla-total-hh {:households
                                                               :gla-households})
                              [:gss-code :year])

          difference (wds/add-derived-column joined-ds :difference
                                             [:households
                                              :gla-households] -)]

      ;; (println difference)

      (is (:households result))
      (is (:dwellings result))

      (is (= (:shape gla-total-hh) (:shape total-hh)))
      (is (= (:column-names gla-total-hh) (:column-names total-hh)))

      (is (every? #(tu/fp-equals? (wds/subset-ds joined-ds :rows % :cols :households)
                                  (wds/subset-ds joined-ds :rows % :cols :gla-households)
                                  300)
                  (range (first (:shape joined-ds)))))))

  (testing "The model is run using the split data"
    ;; You must have run `lein split-data` on the command-line before
    (let [fixed-catalog (mapv #(if (= (:witan/type %) :input)
                                 (add-params-to-local-input %) %)
                              (:catalog m/household-model))
          workspace     {:workflow  (:workflow m/household-model)
                         :catalog   fixed-catalog
                         :contracts (p/available-fns (m/model-library))}
          workspace'    (s/with-fn-validation (wex/build! workspace))
          result        (apply merge (wex/run!! workspace' {}))]

      (is result)
      (is (:households result))
      (is (:total-households result))
      (is (:dwellings result)))))
