(ns witan.models.data-config)

(def default-bucket    "witan-data")
(def default-profile   "witan")
(def default-directory "data/default_datasets")
(def default-folder    "witan.models.household")

(defn valid?
  [filename-regex file]
  (and (not (.isDirectory file))
       (re-find filename-regex (str file))))
