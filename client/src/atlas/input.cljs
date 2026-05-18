(ns atlas.input
  (:require ["three" :as THREE]
            [atlas.ui :as ui]
            [atlas.scene :as scene]
            [atlas.projection :as projection]))

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

(defn set-entity-on-object! [object entity]
  (when object
    (.traverse object
               (fn [child]
                 (let [old-user-data (.-userData child)
                       solid (aget old-user-data "solid")
                       root (aget old-user-data "root")]
                   (set! (.-userData child)
                         #js {:entity (clj->js entity)
                              :solid solid
                              :root root}))))))

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

(defn remove-index [items idx]
  (vec
   (concat
    (subvec (vec items) 0 idx)
    (subvec (vec items) (inc idx)))))

(defn add-contained-item [container item]
  (update container :contains
          (fn [items]
            (conj (vec items) item))))

(defn remove-contained-index [container idx]
  (update container :contains remove-index idx))

(defn player-drop-position [state*]
  (let [player (get @state* :player)
        angle (get @state* :camera-angle)
        pos (.-position player)
        forward-x (- (js/Math.cos angle))
        forward-z (- (js/Math.sin angle))
        distance 1.25]
    [(+ (.-x pos) (* forward-x distance))
     0.05
     (+ (.-z pos) (* forward-z distance))]))

(defn prepare-dropped-entity [state* entity]
  (let [[x _ z] (player-drop-position state*)]
    (-> entity
        (assoc :contains nil)
        (assoc-in [:body :position] [x 0.08 z]))))

(defn add-world-entity! [state* entity]
  (let [scene (get @state* :scene)]
    (when scene
      (projection/entity->mesh!
       entity
       (fn [mesh]
         (.add scene mesh)
         (swap! state* update :solid-objects conj mesh))))))

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

(defn store-in-selected! [state* item-index]
  (let [container (get @state* :selected-entity)
        object (get @state* :selected-object)
        inventory (vec (get @state* :inventory))
        item (get inventory item-index)]
    (when (and container object item)
      (let [updated-container (add-contained-item container item)]

        (set-entity-on-object! object updated-container)

        (swap! state*
               (fn [s]
                 (-> s
                     (assoc :selected-entity updated-container
                            :inventory-open? true)
                     (update :inventory remove-index item-index))))

        (ui/hide-menu!)
        (ui/render-inventory! state*)))))

(defn take-from-selected! [state* item-index]
  (let [container (get @state* :selected-entity)
        object (get @state* :selected-object)
        items (vec (get container :contains))
        item (get items item-index)]
    (when (and container object item)
      (let [updated-container (remove-contained-index container item-index)]

        (set-entity-on-object! object updated-container)

        (swap! state*
               (fn [s]
                 (-> s
                     (assoc :selected-entity updated-container
                            :inventory-open? true)
                     (update :inventory conj item))))

        (ui/hide-menu!)
        (ui/render-inventory! state*)))))

(defn drop-inventory-item! [state* item-index]
  (let [inventory (vec (get @state* :inventory))
        item (get inventory item-index)]
    (when item
      (let [dropped (prepare-dropped-entity state* item)]

        (add-world-entity! state* dropped)

        (swap! state*
               (fn [s]
                 (-> s
                     (assoc :inventory-open? true)
                     (update :inventory remove-index item-index))))

        (ui/render-inventory! state*)))))

(defn store-index-from-event [event]
  (let [target (.-target event)
        idx-str (when target
                  (.getAttribute target "data-store-index"))]
    (when idx-str
      (js/parseInt idx-str 10))))

(defn take-from-index-from-event [event]
  (let [target (.-target event)
        idx-str (when target
                  (.getAttribute target "data-take-from-index"))]
    (when idx-str
      (js/parseInt idx-str 10))))

(defn drop-index-from-event [event]
  (let [target (.-target event)
        idx-str (when target
                  (.getAttribute target "data-drop-index"))]
    (when idx-str
      (js/parseInt idx-str 10))))

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

  (when-let [store-button (ui/store-action-el)]
    (.addEventListener
     store-button
     "click"
     (fn [event]
       (.stopPropagation event)
       (ui/toggle-store-options! state*))))

  (when-let [take-from-button (ui/take-from-action-el)]
    (.addEventListener
     take-from-button
     "click"
     (fn [event]
       (.stopPropagation event)
       (ui/toggle-take-from-options! state*))))

  (when-let [menu (ui/menu-el)]
    (.addEventListener
     menu
     "click"
     (fn [event]
       (when-let [idx (store-index-from-event event)]
         (.stopPropagation event)
         (store-in-selected! state* idx))
       (when-let [idx (take-from-index-from-event event)]
         (.stopPropagation event)
         (take-from-selected! state* idx)))))

  (when-let [inventory-content (ui/inventory-content-el)]
    (.addEventListener
     inventory-content
     "click"
     (fn [event]
       (when-let [idx (drop-index-from-event event)]
         (.stopPropagation event)
         (drop-inventory-item! state* idx)))))

  (when-let [close-button (ui/close-inspect-el)]
    (.addEventListener
     close-button
     "click"
     (fn [event]
       (.stopPropagation event)
       (ui/close-inspection!)))))

(defn solid-bounds [mesh]
  (let [box (THREE/Box3.)]
    (.setFromObject box mesh)
    {:min-x (.-x (.-min box))
     :max-x (.-x (.-max box))
     :min-z (.-z (.-min box))
     :max-z (.-z (.-max box))}))

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
