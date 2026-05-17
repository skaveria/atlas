(ns atlas.ui
  (:require [cljs.pprint :as pprint]))

(defn overlay-content-el []
  (.getElementById js/document "overlay-content"))

(defn menu-el []
  (.getElementById js/document "context-menu"))

(defn inspect-action-el []
  (.getElementById js/document "inspect-action"))

(defn close-inspect-el []
  (.getElementById js/document "close-inspect"))

(defn hide-menu! []
  (when-let [menu (menu-el)]
    (set! (.-display (.-style menu)) "none")))

(defn show-menu! [state* x y entity]
  (swap! state* assoc :selected-entity entity)
  (when-let [menu (menu-el)]
    (set! (.-left (.-style menu)) (str x "px"))
    (set! (.-top (.-style menu)) (str y "px"))
    (set! (.-display (.-style menu)) "block")))

(defn close-inspection! []
  (when-let [content (overlay-content-el)]
    (set! (.-innerText content) "Atlas Prototype 0")))

(defn show-inspection! [entity]
  (hide-menu!)
  (when-let [content (overlay-content-el)]
    (set! (.-innerText content)
          (str "Atlas Prototype 0\n\n"
               "INSPECTING\n"
               (get entity :name)
               (if-let [examine (get entity :examine)]
                 (str "\n\nExamine:\n" examine)
                 "")
               "\n\nRecord:\n"
               (with-out-str
                 (pprint/pprint entity))))))
