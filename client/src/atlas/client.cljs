(ns atlas.client
  (:require ["three" :as THREE]))

(defonce state (atom {}))

(defn make-renderer []
  (doto (THREE/WebGLRenderer. #js {:antialias true})
    (.setSize js/window.innerWidth js/window.innerHeight)))

(defn make-camera []
  (doto (THREE/PerspectiveCamera.
          75
          (/ js/window.innerWidth
             js/window.innerHeight)
          0.1
          1000)
    (aset "position" "x" 6)
    (aset "position" "y" 6)
    (aset "position" "z" 6)
    (.lookAt 0 0 0)))

(defn make-grid []
  (THREE/GridHelper. 20 20))

(defn make-cube []
  (let [geometry (THREE/BoxGeometry. 1 1 1)
        material (THREE/MeshBasicMaterial.
                   #js {:color 0x88aa88})
        mesh (THREE/Mesh. geometry material)]
    (aset mesh "position" "y" 0.5)
    mesh))

(defn animate [renderer scene camera]
  (js/requestAnimationFrame
   #(animate renderer scene camera))
  (.render renderer scene camera))

(defn init! []
  (let [scene (THREE/Scene.)
        renderer (make-renderer)
        camera (make-camera)
        grid (make-grid)
        cube (make-cube)]

    (.add scene grid)
    (.add scene cube)

    (.appendChild
     js/document.body
     (.-domElement renderer))

    (reset! state
            {:scene scene
             :renderer renderer
             :camera camera})

    (animate renderer scene camera)

    (js/console.log "Atlas client initialized")))
