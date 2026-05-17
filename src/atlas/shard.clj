(ns atlas.shard
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn load-shard [path]
  (-> path
      io/resource
      slurp
      edn/read-string))

(def genesis
  (load-shard "seeds/genesis.edn"))
