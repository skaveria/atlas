(ns atlas.server
  (:require [cheshire.core :as json]
            [org.httpkit.server :as http]
            [reitit.ring :as ring]
            [atlas.shard :as shard]
            [atlas.projection :as projection]))

(defonce server* (atom nil))

(defn json-response [x]
  {:status 200
   :headers {"content-type" "application/json; charset=utf-8"
             "access-control-allow-origin" "*"}
   :body (json/generate-string x)})

(def app
  (ring/ring-handler
   (ring/router
    [["/" {:get (fn [_]
                  {:status 200
                   :headers {"content-type" "text/plain; charset=utf-8"}
                   :body "Atlas server online\n"})}]
     ["/api/projection" {:get (fn [_]
                                (json-response
                                 (projection/project-shard shard/genesis)))}]])))

(defn start! []
  (when @server*
    (@server*)
    (reset! server* nil))
  (reset! server*
          (http/run-server #'app {:port 7777}))
  (println "Atlas server online at http://localhost:7777")
  :started)

(defn stop! []
  (when @server*
    (@server*)
    (reset! server* nil)
    (println "Atlas server stopped"))
  :stopped)
