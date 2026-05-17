(ns atlas.scene
  (:require ["three" :as THREE]))

(defn make-renderer []
  (doto (THREE/WebGLRenderer. #js {:antialias true})
    (.setSize js/window.innerWidth js/window.innerHeight)
    (.setPixelRatio js/window.devicePixelRatio)))

(defn make-camera []
  (THREE/PerspectiveCamera.
   75
   (/ js/window.innerWidth js/window.innerHeight)
   0.1
   1000))

(defn make-floor []
  (let [geometry (THREE/PlaneGeometry. 10 10)
        material (THREE/MeshBasicMaterial.
                  #js {:color 0x1f2420
                       :side THREE/DoubleSide})
        floor (THREE/Mesh. geometry material)]
    (set! (.-x (.-rotation floor)) (- (/ js/Math.PI 2)))
    floor))

(defn make-grid []
  (let [grid (THREE/GridHelper. 10 10)]
    (set! (.-y (.-position grid)) 0.01)
    grid))

(defn make-player []
  (let [geometry (THREE/SphereGeometry. 0.35 24 16)
        material (THREE/MeshBasicMaterial. #js {:color 0x88aaff})
        player (THREE/Mesh. geometry material)]
    (set! (.-x (.-position player)) -2)
    (set! (.-y (.-position player)) 0.35)
    (set! (.-z (.-position player)) 2)
    player))

(defn make-scene []
  (let [scene (THREE/Scene.)]
    (set! (.-background scene) (THREE/Color. 0x111111))
    scene))

(defn update-camera! [state*]
  (let [player (get @state* :player)
        camera (get @state* :camera)
        angle (get @state* :camera-angle)
        radius 7
        height 5]
    (when (and player camera)
      (let [pos (.-position player)
            px (.-x pos)
            py (.-y pos)
            pz (.-z pos)]
        (set! (.-x (.-position camera))
              (+ px (* radius (js/Math.cos angle))))
        (set! (.-y (.-position camera))
              (+ py height))
        (set! (.-z (.-position camera))
              (+ pz (* radius (js/Math.sin angle))))
        (.lookAt camera px py pz)))))

(defn install-base-scene! [scene floor grid player]
  (.add scene floor)
  (.add scene grid)
  (.add scene player))
