(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
   [clojure.java.io :as io]
   [clojure.java.javadoc :refer (javadoc)]
   [clojure.pprint :refer (pprint)]
   [clojure.reflect :refer (reflect)]
   [clojure.repl :refer (apropos dir doc find-doc pst source)]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :as test]
   [clojure.tools.namespace.repl :refer (refresh refresh-all)]
   [clojure.tools.trace :ref (trace deftrace trace-forms trace-ns trace-vars)]
   [ring.adapter.jetty :as jetty]
   [site]))

(def system
  "A Var containing an object representing the application under
  development."
  nil)

(defn init
  "Creates and initializes the system under development in the Var
  #'system."
  []
  (alter-var-root #'system (fn [_] (atom {:jetty nil}))))

(defn start
  "Starts the system running, updates the Var #'system."
  []
  (swap! system assoc :jetty
         (jetty/run-jetty site/the-site {:port 3000 :join? false})))

(defn stop
  "Stops the system if it is currently running, updates the Var
  #'system."
  []
  (when-let [jetty (:jetty @system)]
    (.stop jetty)
    (swap! system assoc :jetty nil)))

(defn go
  "Initializes and starts the system running."
  []
  (init)
  (start)
  :ready)

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (stop)
  (refresh :after 'user/go))
