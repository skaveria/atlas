(ns atlas.input
  (:require ["three" :as THREE]
            [atlas.ui :as ui]
            [atlas.scene :as scene]))

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

(defn install-input! [state* renderer]
  (.addEventListener
   (.-domElement renderer)
   "contextmenu"
   #(on-context-menu! state* %))

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
         "q" (swap! state* update :camera-angle - 0.08)
         "e" (swap! state* update :camera-angle + 0.08)
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

  (when-let [close-button (ui/close-inspect-el)]
    (.addEventListener
     close-button
     "click"
     (fn [event]
       (.stopPropagation event)
       (ui/close-inspection!)))))

(defn move-player! [state*]
  (let [player (get @state* :player)
        keys (get @state* :keys)
        speed 0.08]
    (when player
      (let [pos (.-position player)
            dx (+ (if (or (contains? keys "a")
                          (contains? keys "arrowleft"))
                    (- speed)
                    0)
                  (if (or (contains? keys "d")
                          (contains? keys "arrowright"))
                    speed
                    0))
            dz (+ (if (or (contains? keys "w")
                          (contains? keys "arrowup"))
                    (- speed)
                    0)
                  (if (or (contains? keys "s")
                          (contains? keys "arrowdown"))
                    speed
                    0))]

        (set! (.-x pos) (+ (.-x pos) dx))
        (set! (.-z pos) (+ (.-z pos) dz))
        (scene/update-camera! state*)))))
