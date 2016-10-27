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
        (is (every? (set model-ns-fns) library-fns))))
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
      (is (:households result))
      (is (:total-households result))
      (is (:dwellings result)))))

;; Testing the defworkflowfns
;; Helper
(defn- fp-equals? [x y ε] (< (Math/abs (- x y)) ε))

;; Tests
(deftest grp-popn-proj-test
  (testing "The output of the CCM is grouped by 5 years bands"
    (let [popn-proj (read-inputs
                     {:witan/name :input-popn} [] [])
          grouped-popn (grp-popn-proj popn-proj)
          correct-output (ds/dataset (:banded-projections test-outputs))]
      (is (= grouped-popn correct-output)))))

(deftest create-resident-popn-test
  (testing "The resident popn is created from hh and inst popns"
    (let [hh-popn (read-inputs
                   {:witan/name :input-dclg-household-popn} [] [])
          inst-popn (read-inputs
                     {:witan/name :input-dclg-institutional-popn} [] [])
          res-popn (create-resident-popn hh-popn inst-popn)
          correct-output (ds/dataset (:dclg-resident-popn test-outputs))]
      (is (= res-popn correct-output)))))

(deftest sum-resident-popn-test
  (testing "The resident projections are summed by relationship"
    (let [resident-popn (ds/dataset (:dclg-resident-popn test-outputs))
          summed-res-popn (sum-resident-popn resident-popn)
          correct-output (ds/dataset (:resident-popn-summed test-outputs))]
      (is (= summed-res-popn correct-output)))))

(deftest calc-resident-proj-test
  (testing "The resident proj from dclg are adjusted using CCM outputs grouped"
    (let [resident-popn (ds/dataset (:dclg-resident-popn test-outputs))
          banded-projections (ds/dataset (:banded-projections test-outputs))
          resident-popn-summed (ds/dataset (:resident-popn-summed test-outputs))
          witan-res-popn (calc-resident-proj resident-popn
                                                   resident-popn-summed
                                                   banded-projections)
          correct-output (ds/dataset (:witan-resident-popn test-outputs))
          joined-ds (wds/join witan-res-popn
                              (ds/rename-columns correct-output {:resident-popn
                                                                 :resident-popn-test})
                              [:gss-code :year :sex :relationship :age-group])]
      (is (= (:shape witan-res-popn) (:shape correct-output)))
      (is (= (:column-names witan-res-popn) (:column-names correct-output)))
      (is (every? #(fp-equals? (wds/subset-ds joined-ds :rows % :cols :resident-popn)
                               (wds/subset-ds joined-ds :rows % :cols :resident-popn-test)
                               0.000001)
                  (range (first (:shape joined-ds))))))))

(deftest apportion-popn-by-relationship-test
  (testing "The step of apportion of witan popn across relationship types"
    (let [popn-proj (read-inputs {:witan/name :input-popn} [] [])
          hh-popn (read-inputs {:witan/name :input-dclg-household-popn} [] [])
          inst-popn (read-inputs {:witan/name :input-dclg-institutional-popn} [] [])
          witan-res-popn (:resident-popn (apportion-popn-by-relationship-1-0-0
                                          {:population popn-proj
                                           :dclg-household-popn hh-popn
                                           :dclg-institutional-popn inst-popn}))
          correct-output (ds/dataset (:witan-resident-popn test-outputs))
          joined-ds (wds/join witan-res-popn
                              (ds/rename-columns correct-output {:resident-popn
                                                                 :resident-popn-test})
                              [:gss-code :year :sex :relationship :age-group])]
      (is (= (:shape witan-res-popn) (:shape correct-output)))
      (is (= (:column-names witan-res-popn) (:column-names correct-output)))
      (is (every? #(fp-equals? (wds/subset-ds joined-ds :rows % :cols :resident-popn)
                               (wds/subset-ds joined-ds :rows % :cols :resident-popn-test)
                               0.000001)
                  (range (first (:shape joined-ds))))))))

(deftest calc-institutional-popn-test
  (testing "The witan institutional population is calculated properly"
    (let [dclg-inst-popn (read-inputs {:witan/name :input-dclg-institutional-popn} [] [])
          witan-res-popn (ds/dataset (:witan-resident-popn test-outputs))
          dclg-res-popn (ds/dataset (:dclg-resident-popn test-outputs))
          witan-inst-popn (:institutional-popn
                           (calc-institutional-popn-1-0-0 {:dclg-institutional-popn dclg-inst-popn
                                                           :resident-popn witan-res-popn
                                                           :dclg-resident-popn dclg-res-popn}))
          correct-output (ds/dataset (:witan-institutional-popn test-outputs))
          joined-ds (wds/join witan-inst-popn
                              (ds/rename-columns correct-output {:institutional-popn
                                                                 :institutional-popn-test})
                              [:gss-code :year :sex :relationship :age-group])]
      (is (= (:shape witan-inst-popn) (:shape correct-output)))
      (is (= (:column-names witan-inst-popn) (:column-names correct-output)))
      (is (every? #(fp-equals? (wds/subset-ds joined-ds :rows % :cols :institutional-popn)
                               (wds/subset-ds joined-ds :rows % :cols :institutional-popn-test)
                               0.000001)
                  (range (first (:shape joined-ds)))))))
  (testing "The witan institutional population is calculated properly for 75+ popn"
    (let [dclg-inst-popn (read-inputs {:witan/name :input-dclg-inst-popn-with-75+} [] [])
          witan-res-popn (ds/dataset (:witan-resident-popn-with-75+ test-outputs))
          dclg-res-popn (ds/dataset (:dclg-resident-popn-with-75+ test-outputs))
          witan-inst-popn (:institutional-popn
                           (calc-institutional-popn-1-0-0 {:dclg-institutional-popn dclg-inst-popn
                                                           :resident-popn witan-res-popn
                                                           :dclg-resident-popn dclg-res-popn}))
          correct-output (ds/dataset (:witan-institutional-popn-with-75+ test-outputs))
          joined-ds (wds/join witan-inst-popn
                              (ds/rename-columns correct-output {:institutional-popn
                                                                 :institutional-popn-test})
                              [:gss-code :year :sex :relationship :age-group])]
      (is (= (:shape witan-inst-popn) (:shape correct-output)))
      (is (= (:column-names witan-inst-popn) (:column-names correct-output)))
      (is (every? #(fp-equals? (wds/subset-ds joined-ds :rows % :cols :institutional-popn)
                               (wds/subset-ds joined-ds :rows % :cols :institutional-popn-test)
                               0.000001)
                  (range (first (:shape joined-ds))))))))

(deftest calc-household-popn-test
  (testing "The household population gets created!"
    (let [resident-popn (ds/dataset (:witan-resident-popn test-outputs))
          institut-popn (ds/dataset (:witan-institutional-popn test-outputs))
          household-popn (:household-popn
                          (calc-household-popn-1-0-0 {:resident-popn resident-popn
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
                         {:witan/name :input-dclg-household-representative-rates} [] [])
          households-map (calc-households-1-0-0 {:household-popn hh-popn
                                                 :household-representative-rates hh-repr-rates})
          hh-ds (:households households-map)
          total-hh-ds (:total-households households-map)
          correct-hh (ds/dataset (:households test-outputs))
          correct-total-hh (ds/dataset (:total-households test-outputs))
          joined-hh-ds (wds/join hh-ds
                                 (ds/rename-columns correct-hh {:households :test-households})
                                 [:gss-code :year :sex :relationship :age-group])
          joined-total-hh-ds (wds/join total-hh-ds
                                 (ds/rename-columns correct-total-hh {:households :test-households})
                                 [:gss-code :year])]
      (is (= (:shape hh-ds) (:shape correct-hh)))
      (is (= (:shape total-hh-ds) (:shape correct-total-hh)))
      (is (= (:column-names hh-ds) (:column-names correct-hh)))
      (is (= (:column-names total-hh-ds) (:column-names correct-total-hh)))
      (is (every? #(fp-equals? (wds/subset-ds joined-hh-ds :rows % :cols :households)
                               (wds/subset-ds joined-hh-ds :rows % :cols :test-households)
                               0.00001)
                  (range (first (:shape joined-hh-ds)))))
      (is (every? #(fp-equals? (wds/subset-ds joined-total-hh-ds :rows % :cols :households)
                               (wds/subset-ds joined-total-hh-ds :rows % :cols :test-households)
                               0.00001)
                  (range (first (:shape joined-total-hh-ds))))))))

(deftest convert-to-dwellings-test
  (testing "The number of dwellings is calculated correctly"
    (let [total-households (ds/dataset (:total-households test-outputs))
          dclg-dwellings (read-inputs
                             {:witan/name :input-dclg-dwellings} [] [])
          vacancy-dwellings (read-inputs
                          {:witan/name :input-vacancy-dwellings} [] [])
          second-home-rate 0.0
          dwellings-ds (:dwellings (convert-to-dwellings-1-0-0 {:total-households total-households
                                                                :dclg-dwellings dclg-dwellings
                                                                :vacancy-dwellings vacancy-dwellings}
                                                               {:second-home-rate second-home-rate}))
          correct-output (ds/dataset (:dwellings test-outputs))
          joined-ds (wds/join dwellings-ds
                              (ds/rename-columns correct-output {:dwellings :test-dwellings})
                              [:gss-code :year])]
      (println joined-ds)
      (is (= (:shape dwellings-ds) (:shape correct-output)))
      (is (= (:column-names dwellings-ds) (:column-names correct-output)))
      (is (every? #(fp-equals? (wds/subset-ds joined-ds :rows % :cols :dwellings)
                               (wds/subset-ds joined-ds :rows % :cols :test-dwellings)
                               0.00001)
                  (range (first (:shape joined-ds))))))))
