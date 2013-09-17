(ns api-user-sim
  (:require [api-user-agent :refer [post-and-get-data remove-some-data]]
            [clojure.data.generators :as gen]
            [simulant.sim :as sim]
            [simulant.util :as util]
            [datomic.api :as d]))

;; generate tests (api actions) based on a model

;; ... this is where your brain starts to melt

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
  (let [model (-> test :model/_tests first)]
    [[{:db/id (d/tempid :test)
       :agent/_actions (util/e api-user)
       :action/atTime at-time
       :action/type (rand-nth [:action.type/putGet :action.type/delete])}]]))

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
        action-log (util/getx sim/*services* :simulant.sim/actionLog)
        before (System/nanoTime)]
    (f nil) ;; TODO -- test?
    (action-log [{:actionLog/nsec (- (System/nanoTime) before)
                  :db/id (d/tempid :db.part/user)
                  :actionLog/sim (util/e sim)
                  :actionLog/action (util/e action)}])))

(defmethod sim/perform-action :action.type/putGet
  [action process]
  (do-action action process post-and-get-data))

(defmethod sim/perform-action :action.type/delete
  [action process]
  (do-action action process remove-some-data))
