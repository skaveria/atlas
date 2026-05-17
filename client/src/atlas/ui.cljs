(ns atlas.ui
  (:require [cljs.pprint :as pprint]
            [clojure.string :as str]))

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

(defn escape-html [s]
  (-> s
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

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
  (when-let [content (overlay-content-el)]
    (set! (.-innerHTML content) "Atlas Prototype 0")))

(defn show-inspection! [entity]
  (hide-menu!)
  (when-let [content (overlay-content-el)]
    (let [record-text (with-out-str
                        (pprint/pprint entity))
          examine (or (get entity :examine)
                      (get-in entity [:inspect :examine]))]
      (set! (.-innerHTML content)
            (str "<div class=\"inspect-title\">Atlas Prototype 0</div>"
                 "<div class=\"inspect-section\">INSPECTING</div>"
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
