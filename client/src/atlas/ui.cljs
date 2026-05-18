(ns atlas.ui
  (:require [cljs.pprint :as pprint]
            [clojure.string :as str]))

(defn overlay-el [] (.getElementById js/document "overlay"))
(defn overlay-content-el [] (.getElementById js/document "overlay-content"))
(defn menu-el [] (.getElementById js/document "context-menu"))
(defn inspect-action-el [] (.getElementById js/document "inspect-action"))
(defn take-action-el [] (.getElementById js/document "take-action"))
(defn store-action-el [] (.getElementById js/document "store-action"))
(defn take-from-action-el [] (.getElementById js/document "take-from-action"))
(defn close-inspect-el [] (.getElementById js/document "close-inspect"))
(defn hover-tooltip-el [] (.getElementById js/document "hover-tooltip"))
(defn inventory-panel-el [] (.getElementById js/document "inventory-panel"))
(defn inventory-content-el [] (.getElementById js/document "inventory-content"))

(defn ensure-options-el! [id class-name]
  (if-let [el (.getElementById js/document id)]
    el
    (when-let [menu (menu-el)]
      (let [el (.createElement js/document "div")]
        (set! (.-id el) id)
        (set! (.-className el) class-name)
        (.appendChild menu el)
        el))))

(defn ensure-store-options-el! []
  (ensure-options-el! "store-options" "store-options"))

(defn ensure-take-from-options-el! []
  (ensure-options-el! "take-from-options" "take-from-options"))

(defn entity-id-str [entity]
  (str (get entity :id)))

(defn plant-pot? [entity]
  (or (= (get entity :id) :object/plantpot-001)
      (= (entity-id-str entity) "object/plantpot-001")
      (= (entity-id-str entity) ":object/plantpot-001")))

(defn crate? [entity]
  (or (str/includes? (str/lower-case (entity-id-str entity)) "crate")
      (str/includes? (str/lower-case (str (get entity :name))) "crate")))

(defn escape-html [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn hide-options! []
  (doseq [el [(ensure-store-options-el!)
              (ensure-take-from-options-el!)]]
    (when el
      (set! (.-display (.-style el)) "none")
      (set! (.-innerHTML el) ""))))

(defn hide-menu! []
  (when-let [menu (menu-el)]
    (set! (.-display (.-style menu)) "none"))
  (hide-options!))

(defn render-store-options! [state*]
  (when-let [options (ensure-store-options-el!)]
    (let [items (get @state* :inventory)]
      (set! (.-innerHTML options)
            (apply str
                   (map-indexed
                    (fn [idx item]
                      (str "<div class=\"context-item store-item\" data-store-index=\""
                           idx
                           "\">"
                           (escape-html (get item :name))
                           "</div>"))
                    items)))
      (set! (.-display (.-style options))
            (if (seq items) "block" "none")))))

(defn render-take-from-options! [state*]
  (when-let [options (ensure-take-from-options-el!)]
    (let [items (get-in @state* [:selected-entity :contains])]
      (set! (.-innerHTML options)
            (apply str
                   (map-indexed
                    (fn [idx item]
                      (str "<div class=\"context-item take-from-item\" data-take-from-index=\""
                           idx
                           "\">"
                           (escape-html (get item :name))
                           "</div>"))
                    items)))
      (set! (.-display (.-style options))
            (if (seq items) "block" "none")))))

(defn toggle-store-options! [state*]
  (when-let [options (ensure-store-options-el!)]
    (set! (.-display (.-style (ensure-take-from-options-el!))) "none")
    (set! (.-innerHTML (ensure-take-from-options-el!)) "")
    (if (= "block" (.-display (.-style options)))
      (do
        (set! (.-display (.-style options)) "none")
        (set! (.-innerHTML options) ""))
      (render-store-options! state*))))

(defn toggle-take-from-options! [state*]
  (when-let [options (ensure-take-from-options-el!)]
    (set! (.-display (.-style (ensure-store-options-el!))) "none")
    (set! (.-innerHTML (ensure-store-options-el!)) "")
    (if (= "block" (.-display (.-style options)))
      (do
        (set! (.-display (.-style options)) "none")
        (set! (.-innerHTML options) ""))
      (render-take-from-options! state*))))

(defn show-menu! [state* x y entity object]
  (swap! state* assoc
         :selected-entity entity
         :selected-object object)

  (when-let [inspect-el (inspect-action-el)]
    (set! (.-innerText inspect-el)
          (str "Inspect: " (get entity :name))))

  (when-let [take-el (take-action-el)]
    (set! (.-innerText take-el)
          (str "Take: " (get entity :name)))
    (set! (.-display (.-style take-el))
          (if (plant-pot? entity) "block" "none")))

  (when-let [store-el (store-action-el)]
    (set! (.-innerText store-el) "Store...")
    (set! (.-display (.-style store-el))
          (if (and (crate? entity)
                   (seq (get @state* :inventory)))
            "block"
            "none")))

  (when-let [take-from-el (take-from-action-el)]
    (set! (.-innerText take-from-el) "Take From...")
    (set! (.-display (.-style take-from-el))
          (if (and (crate? entity)
                   (seq (get entity :contains)))
            "block"
            "none")))

  (hide-options!)

  (when-let [menu (menu-el)]
    (set! (.-left (.-style menu)) (str x "px"))
    (set! (.-top (.-style menu)) (str y "px"))
    (set! (.-display (.-style menu)) "block")))

(defn show-hover-tooltip! [x y entity]
  (when-let [tooltip (hover-tooltip-el)]
    (set! (.-innerText tooltip) (str (get entity :name)))
    (set! (.-left (.-style tooltip)) (str (+ x 12) "px"))
    (set! (.-top (.-style tooltip)) (str (+ y 12) "px"))
    (set! (.-display (.-style tooltip)) "block")))

(defn hide-hover-tooltip! []
  (when-let [tooltip (hover-tooltip-el)]
    (set! (.-display (.-style tooltip)) "none")))

(defn highlight-edn [edn-text]
  (-> edn-text
      escape-html
      (str/replace #"(:[A-Za-z0-9_\-!?*/+.]+)"
                   "<span class=\"edn-keyword\">$1</span>")
      (str/replace #"(&quot;[^&]*?&quot;)"
                   "<span class=\"edn-string\">$1</span>")
      (str/replace #"\b([-]?\d+(\.\d+)?)\b"
                   "<span class=\"edn-number\">$1</span>")
      (str/replace #"(#\{|\{|\}|\[|\]|\(|\))"
                   "<span class=\"edn-bracket\">$1</span>")))

(defn close-inspection! []
  (when-let [overlay (overlay-el)]
    (set! (.-display (.-style overlay)) "none")))

(defn show-inspection! [entity]
  (hide-menu!)
  (hide-hover-tooltip!)

  (when-let [overlay (overlay-el)]
    (set! (.-display (.-style overlay)) "block"))

  (when-let [content (overlay-content-el)]
    (let [record-text (with-out-str (pprint/pprint entity))
          examine (or (get entity :examine)
                      (get-in entity [:inspect :examine]))]
      (set! (.-innerHTML content)
            (str "<div class=\"inspect-title\">Atlas Entity</div>"
                 "<div class=\"inspect-section\">Inspecting</div>"
                 "<div class=\"inspect-name\">"
                 (escape-html (get entity :name))
                 "</div>"

                 (if examine
                   (str "<div class=\"inspect-section\">Examine</div>"
                        "<div class=\"inspect-examine\">"
                        (escape-html examine)
                        "</div>")
                   "")

                 "<details class=\"record-details\">"
                 "<summary class=\"inspect-section record-summary\">Record</summary>"
                 "<pre class=\"edn-record\">"
                 (highlight-edn record-text)
                 "</pre>"
                 "</details>")))))

(defn render-inventory! [state*]
  (let [open? (get @state* :inventory-open?)
        items (get @state* :inventory)]
    (when-let [panel (inventory-panel-el)]
      (set! (.-display (.-style panel))
            (if open? "block" "none")))

    (when-let [content (inventory-content-el)]
      (set! (.-innerHTML content)
            (if (seq items)
              (apply str
                     (map-indexed
                      (fn [idx item]
                        (str "<div class=\"inventory-item\">"
                             "<span class=\"inventory-item-name\">"
                             (escape-html (get item :name))
                             "</span>"
                             "<button class=\"inventory-drop\" data-drop-index=\""
                             idx
                             "\">Drop</button>"
                             "</div>"))
                      items))
              "<div class=\"inventory-empty\">Empty</div>")))))

(defn toggle-inventory! [state*]
  (swap! state* update :inventory-open? not)
  (render-inventory! state*))
