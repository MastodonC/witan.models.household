(defproject witan.models.household "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [witan.workspace-api "0.1.20"]
                 [witan.workspace-executor "0.2.6"
                  :exclusions [witan.workspace-api]]
                 [prismatic/schema "1.1.3"]])
