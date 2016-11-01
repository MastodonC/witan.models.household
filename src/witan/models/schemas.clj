(ns ^{:doc "Defines the schemas for the inputs/outputs."}
 witan.models.schemas
  (:require [schema.core :as s]))

;; Generate schemas
(defn make-ordered-ds-schema [col-vec]
  {:column-names (mapv #(s/one (s/eq (first %)) (str (first %))) col-vec)
   :columns (mapv #(s/one [(second %)] (format "col %s" (name (first %)))) col-vec)
   s/Keyword s/Any})

(defn make-row-schema
  [col-schema]
  (mapv (fn [s] (let [datatype (-> s :schema first)
                      fieldname (:name s)]
                  (s/one datatype fieldname)))
        (:columns col-schema)))

(defn make-col-names-schema
  [col-schema]
  (mapv (fn [s] (let [datatype (:schema s)
                      fieldname (:name s)]
                  (s/one datatype fieldname)))
        (:column-names col-schema)))

;;Define schemas
(def PopulationProjections
  (make-ordered-ds-schema [[:gss-code s/Str] [:age s/Int] [:sex (s/enum "F" "M")]
                           [:year s/Int] [:population java.lang.Double]]))

(def HouseholdPopulation
  (make-ordered-ds-schema [[:gss-code s/Str] [:age-group s/Keyword]
                           [:sex (s/enum "F" "M")]
                           [:year s/Int] [:relationship s/Str]
                           [:household-popn java.lang.Double]]))

(def DclgHouseholdPopulation
  (make-ordered-ds-schema [[:gss-code s/Str] [:age-group s/Keyword]
                           [:sex (s/enum "F" "M")]
                           [:year s/Int] [:relationship s/Str]
                           [:dclg-household-popn java.lang.Double]]))

(def InstitutionalPopulation
  (make-ordered-ds-schema [[:gss-code s/Str] [:age-group s/Keyword]
                           [:sex (s/enum "F" "M")]
                           [:year s/Int] [:relationship s/Str]
                           [:institutional-popn java.lang.Double]]))

(def DclgInstitutionalPopulation
  (make-ordered-ds-schema [[:gss-code s/Str] [:age-group s/Keyword]
                           [:sex (s/enum "F" "M")]
                           [:year s/Int] [:relationship s/Str]
                           [:dclg-institutional-popn java.lang.Double]]))

(def ResidentPopulation
  (make-ordered-ds-schema [[:gss-code s/Str] [:age-group s/Keyword]
                           [:sex (s/enum "F" "M")]
                           [:year s/Int] [:relationship s/Str]
                           [:resident-popn java.lang.Double]]))

(def DclgResidentPopulation
  (make-ordered-ds-schema [[:gss-code s/Str] [:age-group s/Keyword]
                           [:sex (s/enum "F" "M")]
                           [:year s/Int] [:relationship s/Str]
                           [:dclg-resident-popn java.lang.Double]]))

(def HouseholdRepresentativeRates
  (make-ordered-ds-schema [[:gss-code s/Str] [:age-group s/Keyword]
                           [:sex (s/enum "F" "M")] [:year s/Int]
                           [:relationship s/Str]
                           [:hh-repr-rates java.lang.Double]]))

(def VacancyDwellings
  (make-ordered-ds-schema [[:gss-code s/Str] [:year s/Int]
                           [:vacancy-dwellings java.lang.Double]]))

(def SecondHomesRates
  (make-ordered-ds-schema [[:gss-code s/Str] [:year s/Int]
                           [:second-homes-rates java.lang.Double]]))



(def Households
  (make-ordered-ds-schema [[:gss-code s/Str] [:year s/Int] [:sex (s/enum "F" "M")]
                           [:relationship s/Str] [:age-group s/Keyword]
                           [:households java.lang.Double]]))

(def TotalHouseholds
  (make-ordered-ds-schema [[:gss-code s/Str] [:year s/Int]
                           [:households java.lang.Double]]))

(def Dwellings
  (make-ordered-ds-schema [[:gss-code s/Str] [:year s/Int]
                           [:dwellings java.lang.Double]]))
