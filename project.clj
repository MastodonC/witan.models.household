(defproject witan.models.household "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [net.mikera/core.matrix "0.55.0"]
                 [witan.workspace-api "0.1.20"]
                 [prismatic/schema "1.1.3"]
                 [org.clojure/data.csv "0.1.3"]]
  :profiles {:dev {:dependencies [[witan.workspace-executor "0.2.6"
                                   :exclusions [witan.workspace-api]]
                                  [environ "1.0.1"]]}
             :data {:source-paths ["src-data"]
                    :dependencies [[amazonica "0.3.73"]
                                   [me.raynes/fs "1.4.6"]]}}
  :aliases {"split-data"  ["with-profile" "data" "run" "-m" "witan.models.split-data"]
            "upload-data" ["with-profile" "data" "run" "-m" "witan.models.upload-data"]})
