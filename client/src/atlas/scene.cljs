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

(defn make-checker-texture []
  (let [canvas (.createElement js/document "canvas")
        ctx (.getContext canvas "2d")
        size 512
        cells 16
        cell-size (/ size cells)]
    (set! (.-width canvas) size)
    (set! (.-height canvas) size)

    (doseq [x (range cells)
            y (range cells)]
      (set! (.-fillStyle ctx)
            (if (even? (+ x y))
              "#202820"
              "#151815"))
      (.fillRect ctx
                 (* x cell-size)
                 (* y cell-size)
                 cell-size
                 cell-size))

    (let [texture (THREE/CanvasTexture. canvas)]
      (set! (.-wrapS texture) THREE/RepeatWrapping)
      (set! (.-wrapT texture) THREE/RepeatWrapping)
      (.set (.-repeat texture) 4 4)
      texture)))

(defn make-floor []
  (let [geometry (THREE/PlaneGeometry. 24 24)
        material (THREE/MeshBasicMaterial.
                  #js {:map (make-checker-texture)
                       :side THREE/DoubleSide})
        floor (THREE/Mesh. geometry material)]
    (set! (.-x (.-rotation floor)) (- (/ js/Math.PI 2)))
    floor))

(defn make-grid []
  (let [grid (THREE/GridHelper. 24 24 0x445044 0x2a302a)]
    (set! (.-y (.-position grid)) 0.012)
    grid))

(defn make-skybox []
  (let [geometry (THREE/SphereGeometry. 80 48 32)
        material (THREE/ShaderMaterial.
                  #js {:side THREE/BackSide
                       :uniforms #js {}
                       :vertexShader
                       "
                       varying vec3 vWorldPosition;

                       void main() {
                         vec4 worldPosition = modelMatrix * vec4(position, 1.0);
                         vWorldPosition = worldPosition.xyz;
                         gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
                       }
                       "
                       :fragmentShader
                       "
                       varying vec3 vWorldPosition;

                       void main() {
                         vec3 dir = normalize(vWorldPosition);
                         float h = dir.y * 0.5 + 0.5;

                         vec3 deep = vec3(0.025, 0.015, 0.055);
                         vec3 mid = vec3(0.12, 0.06, 0.18);
                         vec3 glow = vec3(0.38, 0.20, 0.45);

                         float bands = sin(dir.x * 18.0 + dir.z * 9.0 + dir.y * 7.0) * 0.035;
                         float halo = pow(max(0.0, 1.0 - abs(dir.y - 0.15)), 6.0);

                         vec3 color = mix(deep, mid, h);
                         color = mix(color, glow, halo * 0.55);
                         color += bands;

                         gl_FragColor = vec4(color, 1.0);
                       }
                       "})
        sky (THREE/Mesh. geometry material)]
    sky))

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
  (.add scene (make-skybox))
  (.add scene floor)
  (.add scene grid)
  (.add scene player))
