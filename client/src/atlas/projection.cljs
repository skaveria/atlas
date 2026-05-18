(ns atlas.projection
  (:require ["three" :as THREE]
            [atlas.assets :as assets]))

(defn material-for [material-key]
  (case material-key
    :wood
    (THREE/MeshStandardMaterial.
     #js {:color 0xb8b1a1
          :roughness 0.76
          :metalness 0.02
          :envMapIntensity 0.72})

    "wood"
    (THREE/MeshStandardMaterial.
     #js {:color 0xb8b1a1
          :roughness 0.76
          :metalness 0.02
          :envMapIntensity 0.72})

    (THREE/MeshStandardMaterial.
     #js {:color 0xb0b0b0
          :roughness 0.74
          :metalness 0.02
          :envMapIntensity 0.7})))

(defn apply-transform! [obj body]
  (let [[x y z] (or (get body :position) [0 0 0])
        [sx sy sz] (or (get body :scale) [1 1 1])
        [rx ry rz] (or (get body :rotation) [0 0 0])]
    (set! (.-x (.-position obj)) x)
    (set! (.-y (.-position obj)) y)
    (set! (.-z (.-position obj)) z)

    (set! (.-x (.-scale obj)) sx)
    (set! (.-y (.-scale obj)) sy)
    (set! (.-z (.-scale obj)) sz)

    (set! (.-x (.-rotation obj)) rx)
    (set! (.-y (.-rotation obj)) ry)
    (set! (.-z (.-rotation obj)) rz)

    obj))

(defn attach-entity-data! [obj entity solid? root]
  (set! (.-name obj) (str (get entity :name)))
  (set! (.-userData obj)
        #js {:entity (clj->js entity)
             :solid solid?
             :root root})
  obj)

(defn mark-children! [obj entity solid? root]
  (.traverse obj
             (fn [child]
               (set! (.-userData child)
                     #js {:entity (clj->js entity)
                          :solid solid?
                          :root root})))
  obj)

(defn make-cube [entity]
  (let [body (get entity :body)
        geometry (THREE/BoxGeometry. 1 1 1)
        material (material-for (get body :material))
        mesh (THREE/Mesh. geometry material)]
    (apply-transform! mesh body)
    (attach-entity-data! mesh entity true mesh)
    mesh))

(defn make-model! [entity on-loaded]
  (let [body (get entity :body)
        src (get body :model)]
    (assets/load-model!
     src
     (fn [model]
       (apply-transform! model body)
       (attach-entity-data! model entity true model)
       (mark-children! model entity true model)
       (on-loaded model)))))

(defn entity->mesh! [entity on-loaded]
  (case (get-in entity [:body :form])
    :cube (on-loaded (make-cube entity))
    "cube" (on-loaded (make-cube entity))

    :model (make-model! entity on-loaded)
    "model" (make-model! entity on-loaded)

    nil))

(defn render-projection! [state* scene projection]
  (let [solid-objects* (atom [])]
    (doseq [entity (get projection :entities)]
      (entity->mesh!
       entity
       (fn [mesh]
         (.add scene mesh)
         (when (aget (.-userData mesh) "solid")
           (swap! solid-objects* conj mesh))
         (swap! state* assoc :solid-objects @solid-objects*))))))

(defn fetch-projection! [state* scene]
  (-> (js/fetch "http://localhost:7777/api/projection")
      (.then #(.json %))
      (.then
       (fn [raw]
         (let [projection (js->clj raw :keywordize-keys true)]
           (render-projection! state* scene projection))))
      (.catch
       #(js/console.error "Failed to load Atlas projection" %))))
