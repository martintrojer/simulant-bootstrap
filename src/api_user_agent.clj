(ns api-user-agent
  (:require [clj-http.client :as client]))

;; perform the actual actions

(def url "http://localhost:3000/data")

(defn- post-data [data]
  (client/put url {:body data :content-type :edn :throw-entire-message? true}))

(defn- get-data [id]
  (client/get url {:query-params {:id id}}))

(defn- delete-data [id]
  (client/delete url {:query-params {:id id}}))

;; -----

(def ^:private get-an-id #(-> % :agent/_actions first :agent/siteIds seq rand-nth))

(defn post-some-data [action]
  (let [{:keys [body status]} (post-data (:action/payload action))]
    (-> body read-string :id)))

(defn get-some-data [action]
  (let [id (get-an-id action)]
    [id (get-data id)]))

(defn remove-some-data [action]
  (let [id (get-an-id action)]
    (delete-data id)
    id))
