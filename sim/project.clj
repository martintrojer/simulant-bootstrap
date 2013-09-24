(defproject simulant-bootstrap "0.1.0-SNAPSHOT"
  :description "Simulate site traffic with simulant"
  :url "https://github.com/martintrojer/simulant-bootstrap"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.datomic/simulant "0.1.6"]
                 [clj-http "0.7.6"]]

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/tools.trace "0.7.6"]]
                   :source-paths ["dev"]}})
