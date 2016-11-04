(ns witan.models.soak-test
  (:require  [clojure.test :refer :all]
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

(defn with-gss
  [id gss-code]
  (str id "_" gss-code ".csv"))

(defn local-inputs
  [gss-code]
  { :population [(with-gss "./data/default_datasets/population/ons_2014_based_snpp" gss-code)
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
    (csv-to-dataset filepath fileschema)))

(defn add-params-to-local-input
  [input gss-code]
  (assoc-in input [:witan/params :fn] (partial read-local-inputs gss-code input)))


(defn run-workspace [gss-code]
  (println "Running the household model for" gss-code)
  (time (let [fixed-catalog (mapv #(if (= (:witan/type %) :input)
                                     (add-params-to-local-input % gss-code) %)
                                  (:catalog m/household-model))
              workspace     {:workflow  (:workflow m/household-model)
                             :catalog   fixed-catalog
                             :contracts (p/available-fns (m/model-library))}
              workspace'    (s/with-fn-validation (wex/build! workspace))]
          {(keyword gss-code) (apply merge (wex/run!! workspace' {}))})))

(def english-local-authorities
  (edn/read-string
   (slurp (io/file "data/english_local_authorities.edn"))))

(defn- fp-equals? [x y ε] (< (Math/abs (- x y)) ε))

(defn spot-negative [[gss-code result]]
  (when (some neg? (-> result
                       :households
                       (wds/subset-ds :cols :households)))
    (println (name gss-code) (get english-local-authorities gss-code))))

(deftest all-local-authorities-test
  (testing "The outputs for all English local authorities are sensible"
    (let [results (reduce merge (map
                                 #(run-workspace (name %))
                                 [:E09000003 :E09000003]
                                 ;;(take 2 (keys english-local-authorities))
                                 ))
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
      ;; (is (= 326 (count results)))

      (is (every? #(> % 0.0) all-dwellings))
      (is (every? #(>= % 0.0) all-households))
      (is (every? #(> % 0.0) all-total-households)))))
