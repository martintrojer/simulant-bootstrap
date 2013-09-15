(ns api-user-agent
  (:require [clojure.data.generators :as gen]
            [clj-http.client :as client]))

;; perform the actual actions

(def my-ids (atom #{}))

(def url "http://localhost:3000/data")

(defn post-some-data [test]
  (let [
        ;; this is coming from the test?
        data (zipmap (gen/vec gen/string) (gen/vec gen/scalar))
        _ (println (pr-str data))
        res (client/put url {:body (pr-str data) :content-type :edn :throw-entire-message? true})
        ]
    res
    ))


;; (post-some-data nil)
