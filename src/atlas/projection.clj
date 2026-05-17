(ns atlas.projection)

(defn entity->projected-body [entity]
  (when-let [body (:projection/body entity)]
    {:id (:id entity)
     :kind (:kind entity)
     :name (:name entity)
     :body body
     :inspect entity}))

(defn project-shard [shard]
  {:projection/kind :atlas.projection/grid-world
   :grid {:size [10 10]
          :cell-size 1}
   :entities
   (->> (:entities shard)
        (keep entity->projected-body)
        vec)})
