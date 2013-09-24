(ns site
  (:require [compojure.core :refer (defroutes GET PUT DELETE)]
            [compojure.route :as route]
            [ring.middleware.edn]
            [ring.middleware.keyword-params]
            [ring.middleware.params]))

(def id-ctr (ref 0))
(def store (ref {}))

(defn live-ids []
  (->> @store keys (map read-string)))

(defn generate-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn get-data [params]
  (if-let [data (get @store (:id params))]
    (generate-response data)
    (generate-response :not-found 400)))

(defn store-data [data]
  (dosync
   (let [id @id-ctr]
     (alter id-ctr inc)
     (alter store assoc (str id) data)
     (generate-response {:id id}))))

(defn remove-data [params]
  (if-let [data (get @store (:id params))]
    (dosync
      (alter store dissoc (:id params))
      (generate-response {:id (:id params)}))
    (generate-response :not-found 400)))

(defroutes app-routes
  (GET "/" [] (generate-response :ok))
  (GET "/liveids" [] (generate-response (live-ids)))
  (GET "/data" {params :params} (get-data params))
  (PUT "/data" {params :params} (store-data params))
  (DELETE "/data" {params :params} (remove-data params))
  (route/not-found (generate-response :not-found)))

(def the-site
  (-> app-routes
      (ring.middleware.edn/wrap-edn-params)
      (ring.middleware.keyword-params/wrap-keyword-params)
      (ring.middleware.params/wrap-params)))

;; curl -X GET http://localhost:3000
;; curl -X PUT -H "Content-Type: application/edn" -d '{:name :barnabas}' http://localhost:3000/data
;; curl -X GET http://localhost:3000/data?id=1
;; curl -X DELETE http://localhost:3000/data?id=1
;; curl -X GET http://localhost:3000/liveids
