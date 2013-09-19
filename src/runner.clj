(ns runner
  (:require [api-user-sim :as user-sim]
            [api-user-agent :as user-agent]
            [db]

            [datomic.api :as d]

            [simulant.sim :as sim]
            [simulant.util :as util]

            [clojure.java.io :as io]))

;; ===========================================================
;; Load schemas

(defn load-schema
  [conn resource]
  (let [m (-> resource io/resource slurp read-string)]
    (doseq [v (vals m)]
      (doseq [tx v]
        (d/transact conn tx)))))

;; generic simulation schema
(load-schema db/sim-conn "simulant/schema.edn")

;; schema for this specific sim
(load-schema db/sim-conn "site-sim.edn")

;; ===========================================================
;; model for this sim

(def model-id (d/tempid :model))

(def site-user-model-data
  [{:db/id model-id
    :model/type :model.type/siteUsage
    :model/userCount 100
    :model/meanPayloadSize 2
    :model/meanSecsBetweenHits 10}])

(def site-user-model
  (-> @(d/transact db/sim-conn site-user-model-data)
      (util/tx-ent model-id)))

;; ===========================================================
;; create stuff

;; activity for this sim
(def site-usage-test
  (sim/create-test db/sim-conn site-user-model
                   {:db/id (d/tempid :test)
                    :test/duration (* 10 1000) ;;(util/hours->msec 1)
                    }))

;; sim
(def site-usage-sim
  (sim/create-sim db/sim-conn site-usage-test {:db/id (d/tempid :sim)
                                            :sim/processCount 10}))

;; codebase for the sim
(defn assoc-codebase-tx [entities]
  (let [codebase (util/gen-codebase)
        cid (:db/id codebase)]
    (cons
     codebase
     (mapv #(assoc {:db/id (:db/id %)} :source/codebase cid) entities))))
(d/transact db/sim-conn (assoc-codebase-tx [site-usage-test site-usage-sim]))

;; action log for this sim
(def action-log
  (sim/create-action-log db/sim-conn site-usage-sim))

;; clock for this sim
(def sim-clock (sim/create-fixed-clock db/sim-conn site-usage-sim {:clock/multiplier 960}))

;; ===========================================================
;; run the sim

(comment

  (d/q '[:find ?type
         :where
         [?e :agent/actions ?actions]
         [?e :agent/type ?type]]
       simdb)

  (d/q '[:find ?e
         :where
         [_ :model/tests ?e]
         [?e :test/type ?type]
         ]
       simdb)


  (def pruns
    (->> #(sim/run-sim-process db/sim-uri (:db/id site-usage-sim))
         (repeatedly (:sim/processCount site-usage-sim))
         (into [])))

  ;; wait for sim to finish
  (time
   (mapv (fn [prun] @(:runner prun)) pruns))

  )

;; ===========================================================
;; look at the results

;; grab latest database value so we can validate each of the steps above
(def simdb (d/db db/sim-conn))
