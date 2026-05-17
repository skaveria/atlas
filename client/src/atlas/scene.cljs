(ns atlas.scene
  (:require ["three" :as THREE]
            ["three/examples/jsm/loaders/RGBELoader.js" :refer [RGBELoader]]))

(defn make-renderer []
  (let [renderer (THREE/WebGLRenderer. #js {:antialias true})]
    (.setSize renderer js/window.innerWidth js/window.innerHeight)
    (.setPixelRatio renderer js/window.devicePixelRatio)
    (set! (.-outputColorSpace renderer) THREE/SRGBColorSpace)
    (set! (.-toneMapping renderer) THREE/ACESFilmicToneMapping)
    (set! (.-toneMappingExposure renderer) 1.15)
    renderer))

(defn make-camera []
  (THREE/PerspectiveCamera.
   75
   (/ js/window.innerWidth js/window.innerHeight)
   0.1
   1000))

(defn make-checker-texture []
  (let [canvas (.createElement js/document "canvas")
        ctx (.getContext canvas "2d")
        size 1024
        cells 24
        cell-size (/ size cells)]
    (set! (.-width canvas) size)
    (set! (.-height canvas) size)

    (doseq [x (range cells)
            y (range cells)]
      (set! (.-fillStyle ctx)
            (if (even? (+ x y))
              "#283226"
              "#171b17"))
      (.fillRect ctx
                 (* x cell-size)
                 (* y cell-size)
                 (+ cell-size 1)
                 (+ cell-size 1)))

    (let [texture (THREE/CanvasTexture. canvas)]
      (set! (.-colorSpace texture) THREE/SRGBColorSpace)
      (set! (.-wrapS texture) THREE/ClampToEdgeWrapping)
      (set! (.-wrapT texture) THREE/ClampToEdgeWrapping)
      texture)))

(defn make-floor []
  (let [geometry (THREE/PlaneGeometry. 24 24)
        material (THREE/MeshStandardMaterial.
                  #js {:map (make-checker-texture)
                       :roughness 0.95
                       :metalness 0.02
                       :envMapIntensity 0.45})
        floor (THREE/Mesh. geometry material)]
    (set! (.-x (.-rotation floor)) (- (/ js/Math.PI 2)))
    floor))

(defn make-grid []
  (let [grid (THREE/GridHelper. 24 24 0x6f806f 0x303830)]
    (set! (.-y (.-position grid)) 0.012)
    grid))

(defn load-hdri! [_renderer scene]
  (let [loader (RGBELoader.)]
    (.load loader
           "/hdr/surreal.hdr"
           (fn [texture]
             (set! (.-mapping texture)
                   THREE/EquirectangularReflectionMapping)
             (set! (.-environment scene) texture)
             (js/console.log "HDRI loaded")))))

(defn make-player []
  (let [geometry (THREE/SphereGeometry. 0.35 32 20)
        material (THREE/MeshStandardMaterial.
                  #js {:color 0x88aaff
                       :roughness 0.22
                       :metalness 0.08
                       :envMapIntensity 1.1
                       :emissive 0x101833
                       :emissiveIntensity 0.22})
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
        height (get @state* :camera-height 5)
        radius (get @state* :camera-radius 7)]
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

(defn install-base-scene! [renderer scene floor grid player]
  (load-hdri! renderer scene)
  (.add scene floor)
  (.add scene grid)
  (.add scene player))
