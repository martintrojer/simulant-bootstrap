(defproject simulant-bootstrap "0.1.0-SNAPSHOT"
  :description "Example site under test"
  :url "https://github.com/martintrojer/simulant-bootstrap"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [ring/ring-jetty-adapter "1.2.0"]
                 [fogus/ring-edn "0.2.0"]
                 [compojure "1.1.5"]]

  :plugins [[lein-ring "0.8.3"]]
  :ring {:handler site/the-site}

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/tools.trace "0.7.6"]]
                   :source-paths ["dev"]}}
  :repl-options {:init (user/go)})
