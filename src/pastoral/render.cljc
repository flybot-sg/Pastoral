;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
(ns pastoral.render
  (:require
    [pastoral.fn-graph :as fng]
    [pastoral.registrar :as registrar :refer [register-kind get-value register-value clear-values]]))

(def kind :render)

(register-kind kind)

(def ^:macro fnk #'fng/fnk)
(def ^:macro defnk #'fng/defnk)

(def renderer-fn
  (atom (constantly {})))

(defn render [layout]
  (@renderer-fn layout))

(add-watch
  registrar/kind->id->value
  :renderer
  (fn [k ref old-state new-state]
    (let [old-graph (get old-state kind)
          new-graph (get new-state kind)]
      (when-not (identical? new-graph old-graph)
        (reset! renderer-fn (fng/compile new-graph))))))

(defn reg-render
  "Register the given function `handler` at the key `kw` in the render graph 
  
  Example Use
  -----------

  (reg-render
     :kw
     (fnk [a b]
        (+ a b)))"
  [kw handler]
  (register-value kind kw handler))

;; TODO should this be in the registrar?
(def output-keyword (atom nil))

(defn reg-render-output
  "Register the keyword `kw` as the final keyword in the render graph
  
  Example Use
  -----------

  (reg-render-output
     :layout)"
  [kw]
  (reset! output-keyword kw))