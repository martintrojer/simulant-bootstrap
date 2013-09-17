(ns api-user-agent
  (:require [clojure.data.generators :as gen]
            [clojure.set]
            [clj-http.client :as client]))

;; perform the actual actions

;; this should go in the database
(def my-ids (atom #{}))

(def url "http://localhost:3000/data")

(defn post-some-data [test]
  (let [;; this is coming from the test?
        data (zipmap (gen/vec gen/string) (gen/vec gen/anything))]
    [(client/put url {:body (pr-str data) :content-type :edn :throw-entire-message? true})
     data]))

(defn get-data [id]
  (update-in
   (client/get url {:query-params {:id id}})
   [:body] read-string))

(defn delete-data [id]
  (client/delete url {:query-params {:id id}}))

;; stuff we also want to capture in the sim results
;; -- access times (get / put / delete)
;; -- size of site/store, value of site/ctr

(defn post-and-get-data [test]
  (let [[{:keys [body status]} data] (post-some-data test)
        id (-> body read-string :id)
        {:keys [body]} (get-data id)]
    ;; no assertion here -- just store this somehow
    ;; (assert (= (:body data) (:body body)))
    (swap! my-ids conj id)))

(defn- get-a-id! []
  (first
   (clojure.set/difference @my-ids
                           (swap! my-ids #(disj % (rand-nth (seq %)))))))

(defn remove-some-data [test]
  (delete-data (get-a-id!)))


;; (post-and-get-data nil)
;; (remove-some-data nil)
