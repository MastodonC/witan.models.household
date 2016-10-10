(ns ^{:doc "Defines helper functions for the household model."}
 witan.models.utils)

(def age-grps
  {(range 0 5) :0-4 (range 5 10) :5-9
   (range 10 15) :10-14 (range 15 20) :15-19
   (range 20 25) :20-24 (range 25 30) :25-29
   (range 30 35) :30-34 (range 35 40) :35-39
   (range 40 45) :40-44 (range 45 50) :45-49
   (range 50 55) :50-54 (range 55 60) :55-59
   (range 60 65) :60-64 (range 65 70) :65-69
   (range 70 75) :70-74 (range 75 80) :75-79
   (range 80 85) :80-84 (range 85 125) :85+})

(defn get-age-grp [n]
  (first (keep (fn [grp] (if (some #(= % n) grp)
                           (get age-grps grp)))
               (keys age-grps))))
