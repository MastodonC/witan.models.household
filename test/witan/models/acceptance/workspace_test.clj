(ns witan.models.acceptance.workspace-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [witan.models.schemas :as sc]
            [witan.models.household :refer :all]
            [witan.models.model :as m]
            [witan.workspace-api.protocols :as p]
            [witan.workspace-executor.core :as wex]
            [clojure.core.matrix.dataset :as ds]
            [clojure.data.csv :as data-csv]
            [schema.coerce :as coerce]
            [witan.datasets :as wds]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

;; Testing the model can be run by the workspace executor

;; Helpers
(defn- load-csv
  "Loads csv file with each row as a vector.
   Stored in map separating column-names from data"
  ([filename]
   (let [file (io/file filename)]
     (when (.exists (io/as-file file))
       (let [parsed-csv (data-csv/read-csv (slurp file))
             parsed-data (rest parsed-csv)
             headers (first parsed-csv)]
         {:column-names headers
          :columns (vec parsed-data)})))))

(defn apply-row-schema
  [col-schema csv-data]
  (let [row-schema (sc/make-row-schema col-schema)]
    (map (coerce/coercer row-schema coerce/string-coercion-matcher)
         (:columns csv-data))))

(defn apply-col-names-schema
  [col-schema csv-data]
  (let [col-names-schema (sc/make-col-names-schema col-schema)]
    ((coerce/coercer col-names-schema coerce/string-coercion-matcher)
     (:column-names csv-data))))

(defn apply-schema-coercion [data schema]
  {:column-names (apply-col-names-schema schema data)
   :columns (vec (apply-row-schema schema data))})

(defn csv-to-dataset
  "Takes in a file path and a schema. Creates a dataset with the file
   data after coercing it using the schema."
  [filepath schema]
  (-> (load-csv filepath)
      (apply-schema-coercion schema)
      (as-> {:keys [column-names columns]} (ds/dataset column-names columns))))

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

(defn read-inputs [input _ schema]
  (let [[filepath fileschema] (get test-data (:witan/name input))]
    (csv-to-dataset filepath fileschema)))

(defn add-input-params
  [input]
  (assoc-in input [:witan/params :fn] (partial read-inputs input)))

(defn- fp-equals? [x y ε] (< (Math/abs (- x y)) ε))

;; Test
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
                        (read-inputs
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

      (println difference)

      (is (:households result))
      (is (:dwellings result))

      (is (= (:shape gla-total-hh) (:shape total-hh)))
      (is (= (:column-names gla-total-hh) (:column-names total-hh)))

      (is (every? #(fp-equals? (wds/subset-ds joined-ds :rows % :cols :households)
                               (wds/subset-ds joined-ds :rows % :cols :gla-households)
                               300)
                  (range (first (:shape joined-ds))))))))
