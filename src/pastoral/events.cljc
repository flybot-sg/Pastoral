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
(ns pastoral.events
  (:require
    [pastoral.console :refer [console]]
    [pastoral.db :refer [app-db]]
    [pastoral.debug :refer [debug?]]
    [pastoral.interceptor :as interceptor]
    [pastoral.registrar :refer [register-kind get-value register-value clear-values]]
    [pastoral.trace :as trace]
    [pastoral.utils :refer [first-in-vector]]))

(def kind :event)

(register-kind kind)

(defn- flatten-and-remove-nils
  "`interceptors` might have nested collections, and contain nil elements.
  return a flat collection, with all nils removed.
  This function is 9/10 about giving good error messages."
  [id interceptors]
  (let [make-chain  #(->> % flatten (remove nil?))]
    (if-not debug?
      (make-chain interceptors)
      (do    ;; do a whole lot of development time checks
        (when-not (coll? interceptors)
          (console :error "pastoral: when registering" id ", expected a collection of interceptors, got:" interceptors))
        (let [chain (make-chain interceptors)]
          (when (empty? chain)
            (console :error "pastoral: when registering" id ", given an empty interceptor chain"))
          (when-let [not-i (first (remove interceptor/interceptor? chain))]
            (if (fn? not-i)
              (console :error "pastoral: when registering" id ", got a function instead of an interceptor. Did you provide old style middleware by mistake? Got:" not-i)
              (console :error "pastoral: when registering" id ", expected interceptors, but got:" not-i)))
          chain)))))


(defn register
  "Associate the given event `id` with the given collection of `interceptors`.

   `interceptors` may contain nested collections and there may be nils
   at any level,so process this structure into a simple, nil-less vector
   before registration.

   Typically, an `event handler` will be at the end of the chain (wrapped
   in an interceptor)."
  [id interceptors]
  (register-value kind id (flatten-and-remove-nils id interceptors)))



;; -- handle event --------------------------------------------------------------------------------

(def ^:dynamic *handling* nil)    ;; remember what event we are currently handling

(defn handle
  "Given an event vector `event-v`, look up the associated interceptor chain, and execute it."
  [event-v]
  (let [event-id  (first-in-vector event-v)]
    (if-let [interceptors  (get-value kind event-id true)]
      (if *handling*
        (console :error "pastoral: while handling" *handling* ", dispatch-sync was called for" event-v ". You can't call dispatch-sync within an event handler.")
        (binding [*handling*  event-v]
          (trace/with-trace {:operation event-id
                             :op-type   kind
                             :tags      {:event event-v}}
            (trace/merge-trace! {:tags {:app-db-before @app-db}})
            (interceptor/execute event-v interceptors)
            (trace/merge-trace! {:tags {:app-db-after @app-db}})))))))


