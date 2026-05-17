(ns atlas.input
  (:require ["three" :as THREE]
            [atlas.ui :as ui]
            [atlas.scene :as scene]))

(def player-speed 0.035)
(def player-radius 0.35)
(def drag-rotation-speed 0.008)

(defn entity-at-event [state* event]
  (let [scene (get @state* :scene)
        camera (get @state* :camera)
        renderer (get @state* :renderer)]
    (when (and scene camera renderer)
      (let [rect (.getBoundingClientRect (.-domElement renderer))
            mouse (THREE/Vector2.
                   (- (* 2 (/ (- (.-clientX event) (.-left rect))
                              (.-width rect)))
                      1)
                   (- 1
                      (* 2 (/ (- (.-clientY event) (.-top rect))
                              (.-height rect)))))
            raycaster (THREE/Raycaster.)]
        (.setFromCamera raycaster mouse camera)
        (let [hits (.intersectObjects raycaster (.-children scene) true)]
          (loop [i 0]
            (when (< i (.-length hits))
              (let [hit (aget hits i)
                    obj (.-object hit)
                    raw-entity (aget (.-userData obj) "entity")]
                (if raw-entity
                  (js->clj raw-entity :keywordize-keys true)
                  (recur (inc i)))))))))))

(defn on-context-menu! [state* event]
  (.preventDefault event)
  (if-let [entity (entity-at-event state* event)]
    (ui/show-menu! state* (.-clientX event) (.-clientY event) entity)
    (ui/hide-menu!)))

(defn key-name [event]
  (.toLowerCase (.-key event)))

(defn begin-drag! [state* event]
  (when (= 0 (.-button event))
    (swap! state* assoc
           :dragging-camera? true
           :last-drag-x (.-clientX event))))

(defn drag-camera! [state* event]
  (when (get @state* :dragging-camera?)
    (let [last-x (get @state* :last-drag-x)
          x (.-clientX event)
          dx (- x last-x)]
      (swap! state*
             (fn [s]
               (-> s
                   (update :camera-angle + (* dx drag-rotation-speed))
                   (assoc :last-drag-x x))))
      (scene/update-camera! state*))))

(defn end-drag! [state* _event]
  (swap! state* assoc
         :dragging-camera? false
         :last-drag-x nil))

(defn install-input! [state* renderer]
  (.addEventListener
   (.-domElement renderer)
   "contextmenu"
   #(on-context-menu! state* %))

  (.addEventListener
   (.-domElement renderer)
   "mousedown"
   #(begin-drag! state* %))

  (.addEventListener
   js/document
   "mousemove"
   #(drag-camera! state* %))

  (.addEventListener
   js/document
   "mouseup"
   #(end-drag! state* %))

  (.addEventListener
   js/document
   "click"
   (fn [_]
     (ui/hide-menu!)))

  (.addEventListener
   js/document
   "keydown"
   (fn [event]
     (swap! state* update :keys conj (key-name event))))

  (.addEventListener
   js/document
   "keyup"
   (fn [event]
     (swap! state* update :keys disj (key-name event))))

  (when-let [inspect-button (ui/inspect-action-el)]
    (.addEventListener
     inspect-button
     "click"
     (fn [event]
       (.stopPropagation event)
       (when-let [entity (get @state* :selected-entity)]
         (ui/show-inspection! entity)))))

  (when-let [close-button (ui/close-inspect-el)]
    (.addEventListener
     close-button
     "click"
     (fn [event]
       (.stopPropagation event)
       (ui/close-inspection!)))))

(defn solid-bounds [mesh]
  (let [pos (.-position mesh)
        scale (.-scale mesh)
        half-x (/ (.-x scale) 2)
        half-z (/ (.-z scale) 2)]
    {:min-x (- (.-x pos) half-x)
     :max-x (+ (.-x pos) half-x)
     :min-z (- (.-z pos) half-z)
     :max-z (+ (.-z pos) half-z)}))

(defn circle-intersects-aabb? [x z radius bounds]
  (let [closest-x (min (:max-x bounds) (max (:min-x bounds) x))
        closest-z (min (:max-z bounds) (max (:min-z bounds) z))
        dx (- x closest-x)
        dz (- z closest-z)]
    (< (+ (* dx dx) (* dz dz))
       (* radius radius))))

(defn blocked? [state* x z]
  (some
   (fn [mesh]
     (circle-intersects-aabb? x z player-radius (solid-bounds mesh)))
   (get @state* :solid-objects)))

(defn try-move-player! [state* dx dz]
  (let [player (get @state* :player)
        pos (.-position player)
        old-x (.-x pos)
        old-z (.-z pos)
        next-x (+ old-x dx)
        next-z (+ old-z dz)]

    ;; Axis-separated movement allows sliding along crates.
    (when-not (blocked? state* next-x old-z)
      (set! (.-x pos) next-x))

    (when-not (blocked? state* (.-x pos) next-z)
      (set! (.-z pos) next-z))))

(defn move-player! [state*]
  (let [player (get @state* :player)
        keys (get @state* :keys)
        angle (get @state* :camera-angle)]
    (when player
      (let [forward-x (- (js/Math.cos angle))
            forward-z (- (js/Math.sin angle))
            right-x (js/Math.sin angle)
            right-z (- (js/Math.cos angle))

            move-forward (+ (if (or (contains? keys "w")
                                     (contains? keys "arrowup"))
                               1
                               0)
                             (if (or (contains? keys "s")
                                     (contains? keys "arrowdown"))
                               -1
                               0))

            move-right (+ (if (or (contains? keys "d")
                                   (contains? keys "arrowright"))
                             1
                             0)
                           (if (or (contains? keys "a")
                                   (contains? keys "arrowleft"))
                             -1
                             0))

            dx (* player-speed
                  (+ (* forward-x move-forward)
                     (* right-x move-right)))

            dz (* player-speed
                  (+ (* forward-z move-forward)
                     (* right-z move-right)))]

        (try-move-player! state* dx dz)
        (scene/update-camera! state*)))))
