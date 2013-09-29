(ns runner
  (:require [api-user-sim :as user-sim]
            [api-user-agent :as user-agent]
            [db]

            [datomic.api :as d]

            [simulant.sim :as sim]
            [simulant.util :as util]

            [clojure.java.io :as io]
            [clojure.set :refer [union difference]]

            [clj-http.client :as client]))

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
                    :test/duration (* 10 10000) ;;(util/hours->msec 1)
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

(comment

  ;; grab latest database value so we can validate each of the steps above
  (def simdb (d/db db/sim-conn))

  ;; -----------------
  ;; siteIds for the agents
  ;; ... this is the payloads still in the site (i.e. not removed)

  (def site-ids
    ;; note that this query is across all sims
    (->> (d/q '[:find ?id
                :where
                [_ :agent/siteIds ?id]]
              simdb)
         (map first)
         set))

  (def live-ids
    (-> (client/get "http://localhost:3000/liveids")
        :body
        read-string
        set))

  (assert (empty? (difference site-ids live-ids)))

  ;; -----------------
  ;; get the siteIds for the diffrent actions

  (defn- get-action-site-ids [action-type]
    (->> (d/q '[:find ?id
                :in $ ?action-type
                :where
                [?e :action/type ?action-type]
                [?e :action/siteId ?id]]
              simdb action-type)
         (map first)
         set))

  (def idmap
    {:put (get-action-site-ids :action.type/put)
     :get (get-action-site-ids :action.type/get)
     :rm  (get-action-site-ids :action.type/delete)})

  (assert (= (difference (:put idmap) (:rm idmap))
             live-ids))

  ;; -----------------
  ;; did we get what we put?

  (defn- get-payload-map [action-type payload-attribute]
    (->> (d/q '[:find ?id ?payload
                :in $ ?action-type ?payload-attribute
                :where
                [?e :action/type ?action-type]
                [?e :action/siteId ?id]
                [?e ?payload-attribute ?payload]]
              simdb action-type payload-attribute)
         (map (fn [[id payload]] [id (read-string payload)]))
         (into {})))

  (let [posted-payloads (get-payload-map :action.type/put :action/payload)
        received-payloads (get-payload-map :action.type/get :action/sitePayload)]
    (doseq [[id payload] received-payloads]
      (assert (= payload (posted-payloads id)))))

  ;; -----------------
  ;; some timings

  (def rules
    '[[[actionTime ?sim ?actionType ?action ?nsec]
       [?test :test/sims ?sim]
       [?test :test/agents ?agent]
       [?agent :agent/actions ?action]
       [?action :action/type ?actionType]
       [?log :actionLog/action ?action]
       [?log :actionLog/sim ?sim]
       [?log :actionLog/nsec ?nsec]]])

  (defn- get-avg-time [action-type]
    (d/q '[:find (avg ?nsec)
           :with ?action
           :in $ % ?sim ?action-type
           :where (actionTime ?sim ?action-type ?action ?nsec)]
         simdb rules (:db/id site-usage-sim) action-type))

  (def avg-times
    {:get (get-avg-time :action.type/get)
     :put (get-avg-time :action.type/put)
     :rm (get-avg-time :action.type/delete)})

  )
