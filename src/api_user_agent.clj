(ns api-user-agent
  (:require [clj-http.client :as client]
            [datomic.api :as d]
            [db]))

;; perform the actual actions

(def url "http://localhost:3000/data")

(defn- post-data [data]
  (client/put url {:body data :content-type :edn :throw-entire-message? true}))

(defn- get-data [id]
  (client/get url {:query-params {:id id}}))

(defn- delete-data [id]
  (client/delete url {:query-params {:id id}}))

;; -----

;; (def ^:private get-an-id #(-> % :agent/_actions first :agent/siteIds seq rand-nth))

(defn- get-an-id [action]
  (let [latest-action (d/entity (d/db db/sim-conn) (:db/id action))]
    (-> latest-action :agent/_actions first :agent/siteIds seq rand-nth)))

(defn post-some-data [action]
  (let [{:keys [body status]} (post-data (:action/payload action))]
    (println "posted some data:" (-> body read-string :id))
    (-> body read-string :id)))

(defn get-some-data [action]
  (when-let [id (get-an-id action)]
    (println "got some data:" id)
    [id (get-data id)]))

(defn remove-some-data [action]
  (when-let [id (get-an-id action)]
    (println "remove some data:" id)
    (delete-data id)
    id))
