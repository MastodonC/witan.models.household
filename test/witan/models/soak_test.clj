(ns witan.models.soak-test
  (:require  [clojure.test :refer :all]
             [schema.core :as s]
             [witan.models.schemas :as sc]
             [witan.models.household :refer :all]
             [witan.models.model :as m]
             [witan.workspace-api.protocols :as p]
             [witan.workspace-executor.core :as wex]
             [witan.datasets :as wds]
             [clojure.java.io :as io]
             [clojure.edn :as edn]
             [witan.models.test-utils :as tu]
             [environ.core :refer [env]]))

;; Test the model can run in the workspace for all English local authorities

(defn with-gss
  [id gss-code]
  (str id "_" gss-code ".csv"))

(defn local-inputs
  [gss-code]
  {:population [(with-gss "./data/default_datasets/population/ons_2014_based_snpp" gss-code)
                 sc/PopulationProjections]
   :dclg-household-popn
   [(with-gss "./data/default_datasets/household_population/dclg_2014_hh_popn_proj" gss-code)
    sc/DclgHouseholdPopulation]
   :dclg-institutional-popn
   [(with-gss "./data/default_datasets/institutional_population/dclg_2014_inst_popn_proj" gss-code)
    sc/DclgInstitutionalPopulation]
   :dclg-household-representative-rates
   [(with-gss "./data/default_datasets/household_representative_rates/dclg_2014_hh_repr_rates"
      gss-code)
    sc/HouseholdRepresentativeRates]
   :dclg-dwellings [(with-gss "./data/default_datasets/dwellings/dclg_2015_dwellings" gss-code)
                    sc/Dwellings]
   :vacancy-dwellings
   [(with-gss "./data/default_datasets/vacancy_dwellings/dclg_2015_vacant_dwellings" gss-code)
    sc/VacancyDwellings]})

(defn read-local-inputs [gss-code input _ schema]
  (let [[filepath fileschema] (get (local-inputs gss-code) (:witan/name input))]
    (tu/csv-to-dataset filepath fileschema)))

(defn add-params-to-local-input
  [input gss-code]
  (assoc-in input [:witan/params :fn] (partial read-local-inputs gss-code input)))

(def english-local-authorities
  (edn/read-string
   (slurp (io/file "data/english_local_authorities.edn"))))

(defn run-workspace [gss-code]
  (println "Running the household model for" gss-code
           (get english-local-authorities (keyword gss-code)))
  (let [fixed-catalog (mapv #(if (= (:witan/type %) :input)
                               (add-params-to-local-input % gss-code) %)
                            (:catalog m/household-model))
        workspace     {:workflow  (:workflow m/household-model)
                       :catalog   fixed-catalog
                       :contracts (p/available-fns (m/model-library))}
        workspace'    (s/with-fn-validation (wex/build! workspace))]
    {(keyword gss-code) (apply merge (wex/run!! workspace' {}))}))

(defn spot-negative [[gss-code result]]
  (when (some neg? (-> result
                       :households
                       (wds/subset-ds :cols :households)))
    (println (name gss-code) (get english-local-authorities gss-code))))

;; The following test runs on all English local authorities in over 18 minutes
;; You must have run `lein split-data` on the command-line before

(when (= (:run-soak-test env) "yes")
  (deftest all-local-authorities-test
    (testing "The outputs for all English local authorities are sensible"
      (let [results (time (reduce merge (map
                                         #(run-workspace (name %))
                                         (keys english-local-authorities))))
            _ (map spot-negative results)


            all-dwellings (mapcat #(-> %
                                       :dwellings
                                       (wds/subset-ds :cols :dwellings))
                                  (vals results))
            all-households (mapcat #(-> %
                                        :households
                                        (wds/subset-ds :cols :households))
                                   (vals results))
            all-total-households (mapcat #(-> %
                                              :total-households
                                              (wds/subset-ds :cols :households))
                                         (vals results))]
        (is (= 326 (count results)))
        (is (every? pos? all-dwellings))
        (is (every? #(>= % 0.0) all-households))
        (is (every? pos? all-total-households))))))
