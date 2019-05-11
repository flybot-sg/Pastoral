;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
;;
;; This file incorporates work covered by the following copyright and
;; permission notice:
;;
;;     Copyright (c) 2015-2017 Michael Thompson
;;
;;     Permission is hereby granted, free of charge, to any person obtaining
;;     a copy of this software and associated documentation files (the
;;     "Software"), to deal in the Software without restriction, including
;;     without limitation the rights to use, copy, modify, merge, publish,
;;     distribute, sublicense, and/or sell copies of the Software, and to
;;     permit persons to whom the Software is furnished to do so, subject to
;;     the following conditions:
;;
;;     The above copyright notice and this permission notice shall be included
;;     in all copies or substantial portions of the Software.
;;
;;     THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
;;     OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
;;     MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
;;     IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
;;     CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
;;     TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
;;     SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
(ns pastoral.registrar
  (:require
    [pastoral.debug :refer [debug?]]
    [pastoral.console :refer [console]]))

(def ^:private kinds
  "This atom contains a set of all keywords that represent different kinds of
   values."
  (atom #{}))

(defn register-kind
  "Register kind of value."
  [kind]
  (swap! kinds conj kind)
  kind)

(def kind->id->value
  "This atom contains a register of all values as a two layer map, keyed first
   by kind, then by id of value. Leaf nodes are values."
  (atom {}))

(defn get-value
  ([kind]
   (get @kind->id->value kind))
  ([kind id]
   (get-in @kind->id->value [kind id]))
  ([kind id required?]
   (let [value (get-value kind id)]
     (when debug?
       (when (and required? (nil? value))
         (console :error "no " (str kind) " value registered for " (str id))))
     value)))

(defn register-value
  [kind id value]
  (assert (@kinds kind) (str "kind " kind " not found"))
  (when debug?
    (when (get-value kind id false)
      (console :warn "overwriting " (str kind) " value for " id)))
  (swap! kind->id->value assoc-in [kind id] value)
  value)

(defn clear-values
  ([]
   (reset! kind->id->value {}))
  ([kind]
   (assert (@kinds kind))
   (swap! kind->id->value dissoc kind))
  ([kind id]
   (assert (@kinds kind))
   (if (get-value kind id)
     (swap! kind->id->value update-in [kind] dissoc id)
     (console :warn "can't clear " (str kind) " value for " (str id) ". Value not found."))))
