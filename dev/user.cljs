(ns user
  (:require [shadow.resource :as resource]
            [clojure.pprint :refer [pprint]]
            [reagent.core :as r]
            [meander.epsilon :as m]
            [kuhumcst.facsimile.parse :as parse]
            [kuhumcst.facsimile.style :as style]
            [kuhumcst.facsimile.core :as facsimile]))

(def tei-example
  ;(resource/inline "examples/tei/1151anno-anno-tei.xml"))
  (resource/inline "examples/tei/tei_example.xml"))

(def css-example
  (resource/inline "examples/css/tei.css"))

(def attr-kmap
  {:xml:lang :lang
   :xml:id   :id})

(def ref-type->da-str
  {"org"      "Organisation"
   "pers"     "Person"
   "place"    "Sted"
   "publ"     "Publikation"
   "receiver" "Modtager"
   "sender"   "Afsender"})

(def pred->comp
  {(comp #{:tei-list} first) (fn [this]
                               [:ul
                                (for [child (array-seq (.-children this))]
                                  [:li {:dangerouslySetInnerHTML {:__html (.-innerHTML child)}
                                        :key                     (hash child)}])])
   (comp :data-ref second)   (fn [this]
                               (let [dataset   (.-dataset this)
                                     href      (.-ref dataset)
                                     href-type (ref-type->da-str (.-type dataset))]
                                 [:a {:href  href
                                      :title href-type}
                                  [:slot]]))})

(defn algorithmic-rewrite
  [node]
  (let [matching-comp #(fn [_ pred comp]
                         (when (pred %)
                           (reduced comp)))]
    (reduce-kv (matching-comp node) nil pred->comp)))

(defn meander-rewrite*
  [x]
  (m/rewrite x
    [:tei-list ?attr .
     [:tei-item !x] ...]
    [:ul ?attr .
     [:li !x] ...]

    [_ {:data-ref ?ref :data-type ?type & _} & _]
    [:a {:href  ?ref
         :title (m/app ref-type->da-str ?type)}
     [:slot]]))

(defn hiccup->comp
  [hiccup]
  (when hiccup
    (fn [this] hiccup)))

(def meander-rewrite
  (comp hiccup->comp meander-rewrite*))

(defn app
  []
  (let [initial-hiccup (parse/xml->hiccup tei-example)
        prefix         (name (first initial-hiccup))
        patch-hiccup   (parse/patch-fn prefix attr-kmap meander-rewrite)
        hiccup         (parse/transform patch-hiccup initial-hiccup)
        css            (style/patch-css css-example prefix)
        teiheader      (parse/select hiccup (parse/element :tei-teiheader))
        facsimile      (parse/select hiccup (parse/element :tei-facsimile))
        text           (parse/select hiccup (parse/element :tei-text))
        test-nodes     (parse/select-all hiccup
                                         (parse/element :tei-forename)
                                         (parse/attr {:type "first"}))]
    [:<>
     [:fieldset
      [:legend "Document"]
      [:details
       [:summary "Hiccup"]
       [:pre (with-out-str (pprint hiccup))]]
      [:details
       [:summary "CSS"]
       [:pre css]]
      [facsimile/custom-html hiccup css]]
     [:fieldset
      [:legend "Header"]
      [:details
       [:summary "Hiccup"]
       [:pre (with-out-str (pprint teiheader))]]]
     [:fieldset
      [:legend "Facsimile"]
      [:details
       [:summary "Hiccup"]
       [:pre (with-out-str (pprint facsimile))]]]
     [:fieldset
      [:legend "Text"]
      [:details
       [:summary "Hiccup"]
       [:pre (with-out-str (pprint text))]]]
     [:fieldset
      [:legend "Test output"]
      [:pre (with-out-str (pprint test-nodes))]]]))

(def root
  (js/document.getElementById "app"))

(defn ^:dev/after-load render
  []
  (r/render [app] root))

(defn start-dev
  []
  (println "Started development environment for kuhumcst/facsimile.")
  (render))