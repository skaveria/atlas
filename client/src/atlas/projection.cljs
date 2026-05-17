(ns atlas.projection
  (:require ["three" :as THREE]))

(defn material-for [material-key]
  (case material-key

    :wood
    (THREE/MeshStandardMaterial.
     #js {:color 0x8fae8f
          :roughness 0.72
          :metalness 0.02
          :envMapIntensity 0.65})

    "wood"
    (THREE/MeshStandardMaterial.
     #js {:color 0x8fae8f
          :roughness 0.72
          :metalness 0.02
          :envMapIntensity 0.65})

    (THREE/MeshStandardMaterial.
     #js {:color 0xaaaaaa
          :roughness 0.72
          :metalness 0.02
          :envMapIntensity 0.65})))

(defn make-cube [entity]
  (let [body (get entity :body)
        position (or (get body :position) [0 0.5 0])
        scale (or (get body :scale) [1 1 1])
        material-key (get body :material)
        geometry (THREE/BoxGeometry. 1 1 1)
        material (material-for material-key)
        mesh (THREE/Mesh. geometry material)
        [x y z] position
        [sx sy sz] scale]

    (set! (.-name mesh) (str (get entity :name)))
    (set! (.-userData mesh) #js {:entity (clj->js entity)
                                  :solid true})

    (set! (.-x (.-position mesh)) x)
    (set! (.-y (.-position mesh)) y)
    (set! (.-z (.-position mesh)) z)

    (set! (.-x (.-scale mesh)) sx)
    (set! (.-y (.-scale mesh)) sy)
    (set! (.-z (.-scale mesh)) sz)

    mesh))

(defn entity->mesh [entity]
  (case (get-in entity [:body :form])
    :cube (make-cube entity)
    "cube" (make-cube entity)
    nil))

(defn render-projection! [state* scene projection]
  (let [meshes (->> (get projection :entities)
                    (keep entity->mesh)
                    vec)]
    (doseq [mesh meshes]
      (.add scene mesh))
    (swap! state* assoc :solid-objects meshes)))

(defn fetch-projection! [state* scene]
  (-> (js/fetch "http://localhost:7777/api/projection")
      (.then #(.json %))
      (.then
       (fn [raw]
         (let [projection (js->clj raw :keywordize-keys true)]
           (render-projection! state* scene projection))))
      (.catch
       #(js/console.error "Failed to load Atlas projection" %))))
