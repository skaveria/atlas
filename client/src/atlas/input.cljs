(ns atlas.input
  (:require ["three" :as THREE]
            [atlas.ui :as ui]
            [atlas.scene :as scene]))

(def player-speed 0.035)
(def player-radius 0.35)

(def drag-rotation-speed 0.008)
(def vertical-drag-speed 0.025)

(def zoom-speed 0.004)

(def min-camera-height 2.2)
(def max-camera-height 7.0)

(def min-camera-radius 3.5)
(def max-camera-radius 11.0)

(defn clamp [lo hi x]
  (max lo (min hi x)))

(defn hit-at-event [state* event]
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
                    raw-entity (aget (.-userData obj) "entity")
                    root (aget (.-userData obj) "root")]
                (if raw-entity
                  {:entity (js->clj raw-entity :keywordize-keys true)
                   :object (or root obj)}
                  (recur (inc i)))))))))))

(defn entity-at-event [state* event]
  (some-> (hit-at-event state* event) :entity))

(defn on-context-menu! [state* event]
  (.preventDefault event)
  (ui/hide-hover-tooltip!)

  (if-let [{:keys [entity object]} (hit-at-event state* event)]
    (ui/show-menu! state* (.-clientX event) (.-clientY event) entity object)
    (ui/hide-menu!)))

(defn key-name [event]
  (.toLowerCase (.-key event)))

(defn begin-drag! [state* event]
  (when (= 0 (.-button event))
    (ui/hide-hover-tooltip!)
    (swap! state* assoc
           :dragging-camera? true
           :last-drag-x (.-clientX event)
           :last-drag-y (.-clientY event))))

(defn drag-camera! [state* event]
  (when (get @state* :dragging-camera?)
    (let [last-x (get @state* :last-drag-x)
          last-y (get @state* :last-drag-y)
          x (.-clientX event)
          y (.-clientY event)
          dx (- x last-x)
          dy (- y last-y)]
      (swap! state*
             (fn [s]
               (-> s
                   (update :camera-angle + (* dx drag-rotation-speed))
                   (update :camera-height
                           #(clamp min-camera-height
                                   max-camera-height
                                   (+ (or % 5)
                                      (* dy vertical-drag-speed))))
                   (assoc :last-drag-x x
                          :last-drag-y y))))
      (scene/update-camera! state*))))

(defn end-drag! [state* _event]
  (swap! state* assoc
         :dragging-camera? false
         :last-drag-x nil
         :last-drag-y nil))

(defn zoom-camera! [state* event]
  (.preventDefault event)

  (let [dy (.-deltaY event)]
    (swap! state*
           update
           :camera-radius
           #(clamp min-camera-radius
                   max-camera-radius
                   (+ (or % 7)
                      (* dy zoom-speed)))))

  (scene/update-camera! state*))

(defn update-hover-tooltip! [state* event]
  (let [menu-open?
        (= "block"
           (.-display
            (.-style
             (ui/menu-el))))]

    (if (or (get @state* :dragging-camera?)
            menu-open?)
      (ui/hide-hover-tooltip!)
      (if-let [entity (entity-at-event state* event)]
        (ui/show-hover-tooltip!
         (.-clientX event)
         (.-clientY event)
         entity)
        (ui/hide-hover-tooltip!)))))

(defn remove-solid-object [solid-objects object]
  (vec
   (remove
    (fn [mesh]
      (identical? mesh object))
    solid-objects)))

(defn take-selected! [state*]
  (let [entity (get @state* :selected-entity)
        object (get @state* :selected-object)
        scene (get @state* :scene)]
    (when (and entity object scene)
      (.remove scene object)

      (swap! state*
             (fn [s]
               (-> s
                   (update :inventory conj entity)
                   (update :solid-objects remove-solid-object object)
                   (assoc :inventory-open? true
                          :selected-entity nil
                          :selected-object nil))))

      (ui/hide-menu!)
      (ui/hide-hover-tooltip!)
      (ui/render-inventory! state*))))

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
   (fn [event]
     (drag-camera! state* event)
     (update-hover-tooltip! state* event)))

  (.addEventListener
   js/document
   "mouseup"
   #(end-drag! state* %))

  (.addEventListener
   (.-domElement renderer)
   "wheel"
   #(zoom-camera! state* %))

  (.addEventListener
   js/document
   "click"
   (fn [_]
     (ui/hide-menu!)))

  (.addEventListener
   js/document
   "keydown"
   (fn [event]
     (let [k (key-name event)]
       (case k
         "i" (do
               (.preventDefault event)
               (ui/toggle-inventory! state*))
         nil)

       (swap! state* update :keys conj k))))

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

  (when-let [take-button (ui/take-action-el)]
    (.addEventListener
     take-button
     "click"
     (fn [event]
       (.stopPropagation event)
       (take-selected! state*))))

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
  (let [closest-x (min (:max-x bounds)
                       (max (:min-x bounds) x))
        closest-z (min (:max-z bounds)
                       (max (:min-z bounds) z))
        dx (- x closest-x)
        dz (- z closest-z)]
    (< (+ (* dx dx)
          (* dz dz))
       (* radius radius))))

(defn blocked? [state* x z]
  (some
   (fn [mesh]
     (circle-intersects-aabb?
      x
      z
      player-radius
      (solid-bounds mesh)))
   (get @state* :solid-objects)))

(defn try-move-player! [state* dx dz]
  (let [player (get @state* :player)
        pos (.-position player)
        old-x (.-x pos)
        old-z (.-z pos)
        next-x (+ old-x dx)
        next-z (+ old-z dz)]
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
