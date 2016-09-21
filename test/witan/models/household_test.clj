(ns witan.models.household-test
  (:require [clojure.test :refer :all]
            [witan.models.household :refer :all]
            [witan.models.model :as m]
            [witan.workspace-api.protocols :as p]))

(deftest household-model-test
  (let [library (m/model-library)
        funs    (p/available-fns library)
        model   (first (p/available-models library)) ;; Only one model defined so far
        {:keys [catalog metadata workflow]} model
        {:keys [witan/name witan/version]} metadata]
    (testing "A model is created correctly"
      (is workflow)
      (is catalog)
      (is metadata))
    (testing "The catalog entries match funtions in the model-library"
      (doseq [{:keys [witan/name witan/fn witan/version witan/params]} catalog]
        (let [fnc (some #(when (and (= fn (:witan/name %))
                                    (= version (:witan/version %))) %) funs)]
          (is fnc))))
    (testing "The catalog entries are existing functions"
      (let [library-fns (map #(:witan/impl %) funs)
            model-ns-list (map str (keys (ns-publics 'witan.models.household)))
            model-ns-fns (map #(keyword (format "witan.models.household/%s" %)) model-ns-list)]
        (is (= (set library-fns)
               (set model-ns-fns)))))))
