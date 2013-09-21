(ns api-user-sim
  (:require [api-user-agent :refer [post-some-data get-some-data remove-some-data]]
            [db]
            [clojure.data.generators :as gen]
            [simulant.sim :as sim]
            [simulant.util :as util]
            [datomic.api :as d]))

;; generate tests (api actions) based on a model

;; ... this is where your brain starts to melt

 ;; +-------------------+
 ;; | agent             |*----- 1 to N ---+
 ;; +-------------------+                 |
 ;; | actions           |                \|/
 ;; | type              |           +--------------------+
 ;; | errorDescription  |           | action             |
 ;; +-------------------+           +--------------------+
 ;; | siteIds           |           | atTime             |
 ;; +-------------------+           | type               |
 ;;                                 +--------------------+
 ;;                                 | payload            |
 ;;                                 | sitePayload        |
 ;;                                 | siteId             |
 ;;                                 +--------------------+

(defn create-test
  "Returns test entity"
  [conn model test]
  (util/require-keys test :db/id :test/duration)
  (-> @(d/transact conn [(assoc test
                           :test/type :test.type/putGetDelte
                           :model/_tests (util/e model))])
      (util/tx-ent (:db/id test))))

(defn create-api-users
  "Returns API user ids sorted"
  [conn test]
  (let [model (-> test :model/_tests util/solo)
        ids (repeatedly (:model/userCount model) #(d/tempid :test))
        txresult (->> ids
                      (map (fn [id] {:db/id id
                                    :agent/type :agent.type/apiUser
                                    :test/_agents (util/e test)}))
                      (d/transact conn))]
    (util/tx-entids @txresult ids)))

(defn generate-api-usage
  "Generate some api usage from apiUser ord, based on the model"
  [test api-user at-time]
  (let [model (-> test :model/_tests first)
        size (gen/geometric (/ 1 (:model/meanPayloadSize model)))
        payload (zipmap (gen/vec gen/string size) (gen/vec gen/anything size))
        usage {:db/id (d/tempid :test)
               :agent/_actions (util/e api-user)
               :action/atTime at-time
               :action/type (rand-nth [:action.type/put :action.type/get :action.type/delete])}]
    [[(if (= (:action/type usage) :action.type/put)
        (assoc usage :action/payload (pr-str payload))
        usage)]]))

(defn generate-api-user-usage
  "Generate all actions for user ord, based on model"
  [test api-user]
  (let [model (-> test :model/_tests first)
        limit (:test/duration test)
        step #(gen/geometric (/ 1 (* 1000 (:model/meanSecsBetweenHits model))))]
    (->> (reductions + (repeatedly step))
         (take-while (fn [t] (< t limit)))
         (mapcat #(generate-api-usage test api-user %)))))

(defmethod sim/create-test :model.type/siteUsage
  [conn model test]
  (let [test (create-test conn model test)
        api-users (create-api-users conn test)]
    (util/transact-batch conn (mapcat #(generate-api-user-usage test %) api-users) 1000)
    (d/entity (d/db conn) (util/e test))))

(defmethod sim/create-sim :test.type/putGetDelte
  [sim-conn test sim]
  (-> @(d/transact sim-conn (sim/construct-basic-sim test sim))
      (util/tx-ent (:db/id sim))))

(defn- do-action [action process f]
  (let [sim (-> process :sim/_processes util/only)
        agent (-> action :agent/_actions first)
        action-log (util/getx sim/*services* :simulant.sim/actionLog)
        before (System/nanoTime)
        res (f action)]
    (action-log [{:actionLog/nsec (- (System/nanoTime) before)
                  :db/id (d/tempid :db.part/user)
                  :actionLog/sim (util/e sim)
                  :actionLog/action (util/e action)}])
    res))

(defmethod sim/perform-action :action.type/put
  [action process]
  (let [site-id (do-action action process post-some-data)
        agent (-> action :agent/_actions first)]
    (when site-id
      @(d/transact db/sim-conn [{:db/id (:db/id action) :action/siteId site-id}
                                {:db/id (:db/id agent) :agent/siteIds site-id}]))))

(defmethod sim/perform-action :action.type/get
  [action process]
  (let [[site-id data] (do-action action process get-some-data)]
    (when site-id
      @(d/transact db/sim-conn [{:db/id (:db/id action)
                                 :action/sitePayload (:body data)
                                 :action/siteId site-id}]))))

(defmethod sim/perform-action :action.type/delete
  [action process]
  (let [site-id (do-action action process remove-some-data)
        agents (-> action :agent/_actions)]
    (when (> (count agents) 1)
      (println "** uh-oh... to many agents" (count agents)))
    (when site-id
      @(d/transact db/sim-conn [[:db/add (:db/id action) :action/siteId site-id]
                                [:db/retract (:db/id (first agents)) :agent/siteIds site-id]]))))

;; -------------------

(comment

  (def an-action
    (ffirst
     (d/q '[:find ?actions
            :where
            [?e :agent/actions ?actions]]
          (d/db db/sim-conn))))

  (def an-agent
    (ffirst
     (d/q '[:find ?e
            :where
            [?e :agent/actions ?actions]]
          (d/db db/sim-conn))))

  (d/q `[:find ?ids
         :where
         [~an-agent :agent/siteIds ?ids]]
       (d/db db/sim-conn))

  @(d/transact db/sim-conn [[:db/add an-agent :agent/siteIds 1]])
  @(d/transact db/sim-conn [[:db/retract an-agent :agent/siteIds 0]])



  ;; --------

  (d/q '[:find ?type
         :where
         [?e :action/atTime ?time]
         [?e :action/type ?type]]
       runner/simdb)

  ;; get an action

  (def act
    (second
     (d/q '[:find ?e
            :where
            [?e :action/type :action.type/put]]
          runner/simdb)))

  (d/ident runner/simdb (d/entity runner/simdb (first act)))

  (let [action (d/entity runner/simdb (first act))
        site-id (post-some-data action)
        agent (-> action :agent/_actions first)]
    @(d/transact db/sim-conn [[:db/add (:db/id action) :action/siteId site-id]
                              {:db/id (:db/id agent) :agent/siteIds site-id}]))

  (let [action (d/entity (d/db db/sim-conn) (first act))]
    (-> action :agent/_actions first :agent/siteIds seq rand-nth))



  )
