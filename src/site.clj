(ns site
  (:require [compojure.core :refer (defroutes GET PUT)]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.edn :as ring-edn]))

(def id-ctr (ref 0))
(def store (ref {}))

(defn generate-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn store-data [data]
  (dosync
   (let [id @id-ctr]
     (alter id-ctr inc)
     (alter store assoc (str id) data)
     (generate-response {:id id}))))

(defn get-data [params]
  (if-let [data (get @store (:id params))]
    (generate-response data)
    (generate-response :not-found 400)))

(defroutes app-routes
  (GET "/" [] (generate-response :ok))
  (PUT "/data" {params :params} (generate-response (store-data params)))
  (GET "/data" {params :params} (generate-response (get-data params)))
  (route/not-found (generate-response :not-found)))

(def the-site
  (-> (handler/api app-routes)
      (ring-edn/wrap-edn-params)))

;; curl -X GET http://localhost:3000
;; curl -X PUT -H "Content-Type: application/edn" -d '{:name :barnabas}' http://localhost:3000/data
;; curl -X GET http://localhost:3000/data?id=1
