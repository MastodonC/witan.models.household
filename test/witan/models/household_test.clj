(ns witan.models.household-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [witan.models.household :refer :all]
            [witan.models.model :as m]
            [witan.workspace-api.protocols :as p]
            [witan.workspace-executor.core :as wex]
            [clojure.core.matrix.dataset :as ds]))

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
(def test-inputs
  {:input-resident-popn [{:gss-code "001" :age 30 :sex "F" :year 2015
                          :relationship "S" :resident-popn 4000.0}
                         {:gss-code "001" :age 31 :sex "F" :year 2015
                          :relationship "S" :resident-popn 3700.0}]
   :input-institutional-popn [{:gss-code "001" :age 30 :sex "F" :year 2015
                               :relationship "S" :institutional-popn 2500.0}
                              {:gss-code "001" :age 31 :sex "F" :year 2015
                               :relationship "S" :institutional-popn 1900.0}]
   :input-household-representative-rates [{:gss-code "001" :age 30 :sex "F" :year 2015
                                           :relationship "S" :hh-repr-rates 0.4}
                                          {:gss-code "001" :age 31 :sex "F" :year 2015
                                           :relationship "S" :hh-repr-rates 0.3}]
   :input-vacancy-rates [{:gss-code "001" :year 2015 :vacancy-rates 0.5}]
   :input-second-homes-rates [{:gss-code "001" :year 2015 :second-homes-rates 0.6}]})

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
      (println result)
      (is result)
      (is (:total-households result))
      (is (:dwellings result)))))
