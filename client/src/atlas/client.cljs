(ns atlas.client
  (:require ["three" :as THREE]
            [cljs.core :refer [js->clj]]))

(defonce state (atom {}))

(defn make-renderer []
  (doto (THREE/WebGLRenderer. #js {:antialias true})
    (.setSize js/window.innerWidth js/window.innerHeight)
    (.setPixelRatio js/window.devicePixelRatio)))

(defn make-camera []
  (let [camera (THREE/PerspectiveCamera.
                75
                (/ js/window.innerWidth js/window.innerHeight)
                0.1
                1000)]
    (set! (.-x (.-position camera)) 6)
    (set! (.-y (.-position camera)) 6)
    (set! (.-z (.-position camera)) 6)
    (.lookAt camera 0 0 0)
    camera))

(defn make-grid [projection]
  (let [[w h] (get-in projection [:grid :size] [10 10])
        grid-size (max w h)]
    (THREE/GridHelper. grid-size grid-size)))

(defn material-for [material-key]
  (case material-key
    :wood (THREE/MeshBasicMaterial. #js {:color 0x88aa88})
    "wood" (THREE/MeshBasicMaterial. #js {:color 0x88aa88})
    (THREE/MeshBasicMaterial. #js {:color 0xaaaaaa})))

(defn make-cube [entity]
  (let [{:keys [id name body]} entity
        {:keys [position scale material]} body
        [x y z] (or position [0 0.5 0])
        [sx sy sz] (or scale [1 1 1])
        geometry (THREE/BoxGeometry. 1 1 1)
        mat (material-for material)
        mesh (THREE/Mesh. geometry mat)]

    (set! (.-name mesh) (str name))

    (set! (.-x (.-position mesh)) x)
    (set! (.-y (.-position mesh)) y)
    (set! (.-z (.-position mesh)) z)

    (set! (.-x (.-scale mesh)) sx)
    (set! (.-y (.-scale mesh)) sy)
    (set! (.-z (.-scale mesh)) sz)

    (js/console.log "Created cube mesh for" (str id) mesh)
    mesh))

(defn entity->mesh [entity]
  (case (get-in entity [:body :form])
    :cube (make-cube entity)
    "cube" (make-cube entity)
    nil))

(defn render-projection! [scene projection]
  (js/console.log "Atlas projection loaded" (clj->js projection))

  (.add scene (make-grid projection))

  (doseq [entity (:entities projection)]
    (js/console.log "Rendering entity" (clj->js entity))
    (when-let [mesh (entity->mesh entity)]
      (.add scene mesh))))

(defn fetch-projection! [scene]
  (-> (js/fetch "http://localhost:7777/api/projection")
      (.then #(.json %))
      (.then (fn [raw]
               (let [projection (js->clj raw :keywordize-keys true)]
                 (render-projection! scene projection))))
      (.catch #(js/console.error "Failed to load Atlas projection" %))))

(defn animate [renderer scene camera]
  (js/requestAnimationFrame
   #(animate renderer scene camera))
  (.render renderer scene camera))

(defn init! []
  (let [scene (THREE/Scene.)
        renderer (make-renderer)
        camera (make-camera)]

    (set! (.-background scene) (THREE/Color. 0x111111))

    (.appendChild js/document.body (.-domElement renderer))

    (reset! state
            {:scene scene
             :renderer renderer
             :camera camera})

    (fetch-projection! scene)
    (animate renderer scene camera)

    (js/console.log "Atlas client initialized")))
