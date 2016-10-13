(ns witan.models.household-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [witan.models.household :refer :all]
            [witan.models.model :as m]
            [witan.workspace-api.protocols :as p]
            [witan.workspace-executor.core :as wex]
            [clojure.core.matrix.dataset :as ds]
            [witan.datasets :as wds]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

;; Testing the structure of the model
(deftest validate-models
  (let [library (m/model-library)
        funs    (p/available-fns library)]
    (testing "Are the models valid?"
      (doseq [{:keys [catalog metadata workflow]} (p/available-models library)]
        (let [{:keys [witan/name witan/version]} metadata
              model-name (str name " " version)]
          (testing (str "\n> Model: " model-name)
            (is catalog)
            (is metadata)
            (doseq [{:keys [witan/name witan/fn witan/version witan/params]} catalog]
              (testing (str "\n>> testing catalog entry " name " " version)
                (let [fnc (some #(when (and (= fn (:witan/name %))
                                            (= version (:witan/version %))) %) funs)]
                  (is fnc) ;; if fail, can't find function with this name + version
                  ;; only check 'function' types for params
                  (when (and fnc (= (:witan/type fnc) :function))
                    (let [{:keys [witan/param-schema]} fnc]
                      (when (or params param-schema)
                        (is params)
                        (is param-schema)
                        (is (not (s/check param-schema params)))))))))))))
    (testing "The catalog entries are existing functions"
      (let [library-fns (map #(:witan/impl %) funs)
            model-ns-list (map str (keys (ns-publics 'witan.models.household)))
            model-ns-fns (map #(keyword (format "witan.models.household/%s" %)) model-ns-list)]
        (is (= (set library-fns)
               (set model-ns-fns)))))
    (testing "Are there duplicates in contracts?"
      (let [counts-by-key (reduce (fn [a [k v]]
                                    (assoc a k (count v))) {}
                                  (group-by (juxt :witan/name :witan/version) funs))]
        (doseq [[[name version] num] counts-by-key]
          (testing (str "\n> testing contract function " name " " version)
            (is (= 1 num))))))))

;; Testing the model can be run by the workspace executor
;; Helpers
(def test-data
  (edn/read-string
   (slurp (io/file "data/testing_data.edn"))))

(def test-inputs
  (:test-inputs test-data))

(def test-outputs
  (:test-outputs test-data))

(defn read-inputs [input _ schema]
  (let [data (get test-inputs (:witan/name input))
        data-set (ds/dataset data)]
    data-set))

(defn add-input-params
  [input]
  (assoc-in input [:witan/params :fn] (partial read-inputs input)))

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
          result        (apply merge (wex/run!! workspace' {}))]
      (is result)
      (is (:total-households result))
      (is (:dwellings result)))))

;; Testing the defworkflowfns
;; Helper
(defn- fp-equals? [x y ε] (< (Math/abs (- x y)) ε))

;; Tests
(deftest grp-popn-proj-test
  (testing "The output of the CCM is grouped by 5 years bands"
    (let [popn-proj (read-inputs
                     {:witan/name :input-popn-projections} [] [])
          grouped-popn (:banded-projections (grp-popn-proj-1-0-0 {:population popn-proj}))
          correct-output (ds/dataset (:banded-projections test-outputs))]
      (is (= grouped-popn correct-output)))))

(deftest sum-resident-popn-test
  (testing "The resident projections are summed by household type"
    (let [resident-popn (read-inputs
                         {:witan/name :input-resident-popn-proj} [] [])
          summed-res-popn (:resident-popn-summed (sum-resident-popn-1-0-0
                                                  {:resident-popn resident-popn}))
          correct-output (ds/dataset (:resident-popn-summed test-outputs))]
      (is (= summed-res-popn correct-output)))))

(deftest adjust-resident-proj-test
  (testing "The resident proj from dclg are adjusted using CCM outputs grouped"
    (let [resident-popn (read-inputs
                         {:witan/name :input-resident-popn-proj} [] [])
          banded-projections (ds/dataset (:banded-projections test-outputs))
          resident-popn-summed (ds/dataset (:resident-popn-summed test-outputs))
          adjusted-res-popn (:adjusted-resident-popn
                             (adjust-resident-proj-1-0-0 {:resident-popn resident-popn
                                                          :resident-popn-summed resident-popn-summed
                                                          :banded-projections banded-projections}))
          correct-output (ds/dataset (:adjusted-resident-popn test-outputs))
          joined-ds (wds/join adjusted-res-popn
                              (ds/rename-columns correct-output {:adjusted-resident-popn
                                                                 :adjusted-resident-popn-test})
                              [:gss-code :year :sex :relationship :age-group])]
      (is (= (:shape adjusted-res-popn) (:shape correct-output)))
      (is (= (:column-names adjusted-res-popn) (:column-names correct-output)))
      (is (every? #(fp-equals? (wds/subset-ds joined-ds :rows % :cols :adjusted-resident-popn)
                               (wds/subset-ds joined-ds :rows % :cols :adjusted-resident-popn-test)
                               0.000001)
                  (range (first (:shape joined-ds))))))))

(deftest calc-household-popn-test
  (testing "The household population gets created!"
    (let [resident-popn (ds/dataset (:adjusted-resident-popn test-outputs))
          institut-popn (read-inputs
                         {:witan/name :input-institutional-popn-proj} [] [])
          household-popn (:household-popn
                          (calc-household-popn-1-0-0 {:adjusted-resident-popn resident-popn
                                                      :institutional-popn institut-popn}))
          correct-output (ds/dataset (:household-popn test-outputs))
          joined-ds (wds/join household-popn
                              (ds/rename-columns correct-output {:household-popn
                                                                 :household-popn-test})
                              [:gss-code :year :sex :relationship :age-group])]
      (is (= (:shape household-popn) (:shape correct-output)))
      (is (= (:column-names household-popn) (:column-names correct-output)))
      (is (every? #(fp-equals? (wds/subset-ds joined-ds :rows % :cols :household-popn)
                               (wds/subset-ds joined-ds :rows % :cols :household-popn-test)
                               0.000001)
                  (range (first (:shape joined-ds))))))))


(deftest calc-households-test
  (testing "The household population is turned into households"
    (let [hh-popn (ds/dataset (:household-popn test-outputs))
          hh-repr-rates (read-inputs
                         {:witan/name :input-household-representative-rates} [] [])
          households-ds (:households
                         (calc-households-1-0-0 {:household-popn hh-popn
                                                 :household-representative-rates hh-repr-rates}))
          correct-output (ds/dataset (:households test-outputs))
          joined-ds (wds/join households-ds
                              (ds/rename-columns correct-output {:households :test-households})
                              [:gss-code :year :sex :relationship :age-group])]
      (is (= (:shape households-ds) (:shape correct-output)))
      (is (= (:column-names households-ds) (:column-names correct-output)))
      (is (every? #(fp-equals? (wds/subset-ds joined-ds :rows % :cols :households)
                               (wds/subset-ds joined-ds :rows % :cols :test-households)
                               0.00001)
                  (range (first (:shape joined-ds))))))))

(deftest calc-total-households-test
  (testing "The total numbers of households are calculated per year and gss code"
    (let [households-ds (ds/dataset (:households test-outputs))
          total-households (:total-households (calc-total-households-1-0-0
                                               {:households households-ds}))
          correct-output (ds/dataset
                          (:total-households test-outputs))
          joined-ds (wds/join total-households
                              (ds/rename-columns correct-output {:households :test-households})
                              [:gss-code :year])]
      (is (= (:shape total-households) (:shape correct-output)))
      (is (= (:column-names total-households) (:column-names correct-output)))
      (is (every? #(fp-equals? (wds/subset-ds joined-ds :rows % :cols :households)
                               (wds/subset-ds joined-ds :rows % :cols :test-households)
                               0.0001)
                  (range (first (:shape joined-ds))))))))

(deftest calc-occupancy-rates-test
  (testing "The occupancy rates are calculated correctly"
    (let [vacancy-rates (ds/dataset
                         (:input-vacancy-rates test-inputs))
          second-homes-rates (ds/dataset
                              (:input-second-homes-rates test-inputs))
          occupancy-rates (:occupancy-rates
                           (calc-occupancy-rates-1-0-0 {:vacancy-rates vacancy-rates
                                                        :second-homes-rates second-homes-rates}))
          correct-output (ds/dataset
                          (:occupancy-rates test-outputs))
          joined-ds (wds/join occupancy-rates
                              (ds/rename-columns correct-output {:occupancy-rates :test-rates})
                              [:gss-code :year])]
      (is (= (:shape occupancy-rates) (:shape correct-output)))
      (is (= (:column-names occupancy-rates) (:column-names correct-output)))
      (is (every? #(fp-equals? (wds/subset-ds joined-ds :rows % :cols :occupancy-rates)
                               (wds/subset-ds joined-ds :rows % :cols :test-rates)
                               0.00001)
                  (range (first (:shape joined-ds))))))))

(deftest calc-dwellings-test
  (testing "The number of dwellings is calculated correctly"
    (let [total-households (ds/dataset
                            (:total-households test-outputs))
          occupancy-rates (ds/dataset
                           (:occupancy-rates test-outputs))
          dwellings-ds (:dwellings (calc-dwellings-1-0-0 {:total-households total-households
                                                          :occupancy-rates occupancy-rates}))
          correct-output (ds/dataset
                          (:dwellings test-outputs))
          joined-ds (wds/join dwellings-ds
                              (ds/rename-columns correct-output {:dwellings :test-dwellings})
                              [:gss-code :year])]
      (is (= (:shape dwellings-ds) (:shape correct-output)))
      (is (= (:column-names dwellings-ds) (:column-names correct-output)))
      (is (every? #(fp-equals? (wds/subset-ds joined-ds :rows % :cols :dwellings)
                               (wds/subset-ds joined-ds :rows % :cols :test-dwellings)
                               0.0001)
                  (range (first (:shape joined-ds))))))))
