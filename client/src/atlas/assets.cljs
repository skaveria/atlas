(ns atlas.assets
  (:require ["three/examples/jsm/loaders/GLTFLoader.js" :refer [GLTFLoader]]))

(defonce model-cache* (atom {}))

(defn clone-model [model]
  (.clone model true))

(defn load-model! [src on-loaded]
  (if-let [cached (get @model-cache* src)]
    (on-loaded (clone-model cached))
    (let [loader (GLTFLoader.)]
      (.load loader
             src
             (fn [gltf]
               (let [scene (.-scene gltf)]
                 (swap! model-cache* assoc src scene)
                 (on-loaded (clone-model scene))))
             nil
             (fn [err]
               (js/console.error "Failed to load model" src err))))))
