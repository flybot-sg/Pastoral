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
(ns pastoral.core
  (:require
    [flybot.console]
    [flybot.registrar          :as registrar]
    [flybot.registrar.core     :as registrar.core]
    [pastoral.events           :as events]
    [pastoral.db               :as db]
    [pastoral.fx               :as fx]
    [pastoral.cofx             :as cofx]
    [pastoral.router           :as router]
    [pastoral.interceptor      :as interceptor]
    [pastoral.animation        :as anim]
    [pastoral.render           :as render]
    [pastoral.std-interceptors :as std-interceptors :refer [db-handler->interceptor
                                                             fx-handler->interceptor
                                                             ctx-handler->interceptor]]))

;; -- API ---------------------------------------------------------------------
;;
;; This namespace represents the pastoral API
;;
;; Below, you'll see we've used this technique:
;;   (def  api-name-for-fn    deeper.namespace/where-the-defn-is)
;;
;; So, we promote a `defn` in a deeper namespace "up" to the API
;; via a `def` in this namespace.
;;
;; Turns out, this approach makes it hard:
;;   - to auto-generate API docs
;;   - for IDEs to provide code completion on functions in the API
;;
;; Which is annoying. But there are pros and cons and we haven't
;; yet revisited the decision.  To compensate, we've added more nudity
;; to the docs.
;;


;; -- dispatch ----------------------------------------------------------------
(def dispatch       router/dispatch)
(def dispatch-sync  router/dispatch-sync)

;; -- effects -----------------------------------------------------------------
(def reg-fx      fx/reg-fx)
(def clear-fx    (partial registrar/clear-values fx/kind))  ;; think unreg-fx

;; -- coeffects ---------------------------------------------------------------
(def reg-cofx    cofx/reg-cofx)
(def inject-cofx cofx/inject-cofx)
(def clear-cofx (partial registrar/clear-values cofx/kind)) ;; think unreg-cofx

;; -- animations --------------------------------------------------------------
(def reg-anim         anim/reg-anim)
(def reg-anim-create  anim/reg-anim-create)
(def reg-anim-destroy anim/reg-anim-destroy)
(def clear-anim (partial registrar/clear-values anim/kind)) ;; think unreg-anim

;; -- render ------------------------------------------------------------------

(def reg-render        render/reg-render)
(def reg-render-output render/reg-render-output)

;; -- Events ------------------------------------------------------------------

(defn reg-event-db
  "Register the given event `handler` (function) for the given `id`. Optionally, provide
  an `interceptors` chain.
  `id` is typically a namespaced keyword  (but can be anything)
  `handler` is a function: (db event) -> db
  `interceptors` is a collection of interceptors. Will be flattened and nils removed.
  `handler` is wrapped in its own interceptor and added to the end of the interceptor
   chain, so that, in the end, only a chain is registered.
   Special effects and coeffects interceptors are added to the front of this
   chain."
  ([id handler]
   (reg-event-db id nil handler))
  ([id interceptors handler]
   (events/register id [cofx/inject-db fx/do-fx interceptors (db-handler->interceptor handler)])))


(defn reg-event-fx
  "Register the given event `handler` (function) for the given `id`. Optionally, provide
  an `interceptors` chain.
  `id` is typically a namespaced keyword  (but can be anything)
  `handler` is a function: (coeffects-map event-vector) -> effects-map
  `interceptors` is a collection of interceptors. Will be flattened and nils removed.
  `handler` is wrapped in its own interceptor and added to the end of the interceptor
   chain, so that, in the end, only a chain is registered.
   Special effects and coeffects interceptors are added to the front of the
   interceptor chain.  These interceptors inject the value of app-db into coeffects,
   and, later, action effects."
  ([id handler]
   (reg-event-fx id nil handler))
  ([id interceptors handler]
   (events/register id [cofx/inject-db fx/do-fx interceptors (fx-handler->interceptor handler)])))


(defn reg-event-ctx
  "Register the given event `handler` (function) for the given `id`. Optionally, provide
  an `interceptors` chain.
  `id` is typically a namespaced keyword  (but can be anything)
  `handler` is a function: (context-map event-vector) -> context-map

  This form of registration is almost never used. "
  ([id handler]
   (reg-event-ctx id nil handler))
  ([id interceptors handler]
   (events/register id [cofx/inject-db fx/do-fx interceptors (ctx-handler->interceptor handler)])))

(def clear-event (partial registrar/clear-values events/kind)) ;; think unreg-event-*

;; -- interceptors ------------------------------------------------------------

;; Standard interceptors.
;; Detailed docs on each in std-interceptors.cljs
(def debug       std-interceptors/debug)
(def path        std-interceptors/path)
(def enrich      std-interceptors/enrich)
(def trim-v      std-interceptors/trim-v)
(def after       std-interceptors/after)
(def on-changes  std-interceptors/on-changes)


;; Utility functions for creating your own interceptors
;;
;;  (def my-interceptor
;;     (->interceptor                ;; used to create an interceptor
;;       :id     :my-interceptor     ;; an id - decorative only
;;       :before (fn [context]                         ;; you normally want to change :coeffects
;;                  ... use get-coeffect  and assoc-coeffect
;;                       )
;;       :after  (fn [context]                         ;; you normally want to change :effects
;;                 (let [db (get-effect context :db)]  ;; (get-in context [:effects :db])
;;                   (assoc-effect context :http-ajax {...}])))))
;;
(def ->interceptor   interceptor/->interceptor)
(def get-coeffect    interceptor/get-coeffect)
(def assoc-coeffect  interceptor/assoc-coeffect)
(def get-effect      interceptor/get-effect)
(def assoc-effect    interceptor/assoc-effect)
(def enqueue         interceptor/enqueue)


;; --  logging ----------------------------------------------------------------
;; Internally, pastoral uses the logging functions: warn, log, error, group and groupEnd
;; By default, these functions map directly to the below depending on the platform
;; but you can override with your own fns (set or subset):
;;   - Clojure uses clojure.tools.logging
;;   - Arcadia (Unity + ClojureCLR) uses UnityEngine.Debug
;;   - ClojureCLR uses System.Console
;;   - ClojureScript uses js/console
;;
;; Example Usage:
;;   (defn my-fn [& args]  (post-it-somewhere (apply str args)))  ;; here is my alternative
;;   (pastoral.core/set-loggers!  {:warn my-fn :log my-fn})       ;; override the defaults with mine
(def set-loggers! flybot.console/set-fns!)

;; If you are writing an extension to pastoral, like perhaps
;; an effects handler, you may want to use pastoral logging.
;;
;; usage: (console :error "Oh, dear God, it happened: " a-var " and " another)
;;        (console :warn "Possible breach of containment wall at: " dt)
(def console flybot.console/console)


;; -- unit testing ------------------------------------------------------------

(defn make-restore-fn
  "Checkpoints the state of pastoral and returns a function which, when
  later called, will restore pastoral to that checkpointed state.

  Checkpoint includes app-db and all registered handlers.
  "
  []
  (let [handlers @registrar.core/kind->id->handler
        app-db   @db/app-db]
    (fn []
      ;; TODO Postoral equivalent of subscriptions integration.

      ;; Reset the atoms
      (reset! registrar.core/kind->id->handler handlers)
      (reset! db/app-db app-db)
      nil)))

(defn purge-event-queue
  "Remove all events queued for processing"
  []
  (router/purge pastoral.router/event-queue))

;; -- Event Processing Callbacks  ---------------------------------------------

(defn add-post-event-callback
  "Registers a function `f` to be called after each event is processed
   `f` will be called with two arguments:
    - `event`: a vector. The event just processed.
    - `queue`: a PersistentQueue, possibly empty, of events yet to be processed.

   This is useful in advanced cases like:
     - you are implementing a complex bootstrap pipeline
     - you want to create your own handling infrastructure, with perhaps multiple
       handlers for the one event, etc.  Hook in here.
     - libraries providing 'isomorphic javascript' rendering on  Nodejs or Nashorn.

  'id' is typically a keyword. Supplied at \"add time\" so it can subsequently
  be used at \"remove time\" to get rid of the right callback.
  "
  ([f]
   (add-post-event-callback f f))   ;; use f as its own identifier
  ([id f]
   (router/add-post-event-callback pastoral.router/event-queue id f)))


(defn remove-post-event-callback
  [id]
  (router/remove-post-event-callback pastoral.router/event-queue id))
