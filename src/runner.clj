(ns runner
  (:require [api-user-sim :as user-sim]
            [api-user-agent :as user-agent]
            [datomic.api :as d]

            [simulant.sim :as sim]
            [simulant.util :as util]

            [clojure.java.io :as io]))

;; ===========================================================
;; Helpers

(defn reset-conn
  "Reset connection to a scratch database. Use memory database if no
   URL passed in."
  ([]
     (reset-conn (str "datomic:mem://" (d/squuid))))
  ([uri]
     (d/delete-database uri)
     (d/create-database uri)
     (d/connect uri)))

(defn load-schema
  [conn resource]
  (let [m (-> resource io/resource slurp read-string)]
    (doseq [v (vals m)]
      (doseq [tx v]
        (d/transact conn tx)))))

;; ===========================================================
;; Connect and load schemas

(def sim-uri (str "datomic:mem://" (d/squuid)))

;; (java.util.Date. (d/squuid-time-millis (d/squuid)))

(def sim-conn (reset-conn sim-uri))

;; generic simulation schema
(load-schema sim-conn "simulant/schema.edn")

;; schema for this specific sim
(load-schema sim-conn "site-sim.edn")

;; ===========================================================
;; model for this sim

(def model-id (d/tempid :model))

(def site-user-model-data
  [{:db/id model-id
    :model/type :model.type/siteTraffic
    :model/userCount 100
    :model/meanPayloadSize 100
    :model/meanSecsBetweenHits 10}])

(def site-user-model
  (-> @(d/transact sim-conn site-user-model-data)
      (util/tx-ent model-id)))

;; ===========================================================
;; activity for this sim

(def trading-test (sim/create-test sim-conn site-user-model
                                   {:db/id (d/tempid :test)
                                    :test/duration (util/hours->msec 4)}))
