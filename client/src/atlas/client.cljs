(ns atlas.client
  (:require [atlas.scene :as scene]
            [atlas.projection :as projection]
            [atlas.input :as input]))

(defonce state
  (atom {:scene nil
         :renderer nil
         :camera nil
         :player nil
         :selected-entity nil
         :solid-objects []
         :keys #{}
         :camera-angle 0.75
         :dragging-camera? false
         :last-drag-x nil}))

(defn animate [renderer scene camera]
  (js/requestAnimationFrame
   #(animate renderer scene camera))
  (input/move-player! state)
  (.render renderer scene camera))

(defn init! []
  (let [scene (scene/make-scene)
        renderer (scene/make-renderer)
        camera (scene/make-camera)
        floor (scene/make-floor)
        grid (scene/make-grid)
        player (scene/make-player)]

   (scene/install-base-scene!
 renderer
 scene
 floor
 grid
 player)

    (.appendChild js/document.body (.-domElement renderer))

    (reset! state
            {:scene scene
 :renderer renderer
 :camera camera
 :player player
 :selected-entity nil
 :solid-objects []
 :keys #{}
 :camera-angle 0.75
             :camera-height 5
             :camera-radius 7
 :dragging-camera? false
 :last-drag-x nil
 :last-drag-y nil})

    (input/install-input! state renderer)
    (projection/fetch-projection! state scene)
    (scene/update-camera! state)
    (animate renderer scene camera)

    (js/console.log "Atlas client initialized")))
