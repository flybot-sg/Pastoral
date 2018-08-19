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
(ns pastoral.fx
  (:require
    [pastoral.console :refer [console]]
    [pastoral.db :refer [app-db]]
    [pastoral.events :as events]
    [pastoral.interceptor :refer [->interceptor]]
    [pastoral.registrar :refer [register-kind get-value register-value clear-values]]
    [pastoral.router :as router]
    [pastoral.trace :as trace]))

;; -- Registration ------------------------------------------------------------

(def kind :fx)

(register-kind kind)

(defn reg-fx
  "Register the given effect `handler` for the given `id`.

  `id` is keyword, often namespaced.
  `handler` is a side-effecting function which takes a single argument and whose return
  value is ignored.

  Example Use
  -----------

  First, registration ... associate `:effect2` with a handler.

  (reg-fx
     :effect2
     (fn [value]
        ... do something side-effect-y))

  Then, later, if an event handler were to return this effects map ...

  {...
   :effect2  [1 2]}

   ... then the `handler` `fn` we registered previously, using `reg-fx`, will be
   called with an argument of `[1 2]`."
  [id handler]
  (register-value kind id handler))

;; -- Interceptor -------------------------------------------------------------

(def do-fx
  "An interceptor whose `:after` actions the contents of `:effects`. As a result,
  this interceptor is Domino 3.

  This interceptor is silently added (by reg-event-db etc) to the front of
  interceptor chains for all events.

  For each key in `:effects` (a map), it calls the registered `effects handler`
  (see `reg-fx` for registration of effect handlers).

  So, if `:effects` was:
      {:dispatch  [:hello 42]
       :db        {...}
       :undo      \"set flag\"}

  it will call the registered effect handlers for each of the map's keys:
  `:dispatch`, `:undo` and `:db`. When calling each handler, provides the map
  value for that key - so in the example above the effect handler for :dispatch
  will be given one arg `[:hello 42]`.

  You cannot rely on the ordering in which effects are executed."
  (->interceptor
    :id :do-fx
    :after (fn do-fx-after
             [context]
             (trace/with-trace
               {:op-type :event/do-fx}
               (doseq [[effect-key effect-value] (:effects context)]
                 (if-let [effect-fn (get-value kind effect-key false)]
                   (effect-fn effect-value)
                   (console :error "pastoral: no handler registered for effect:" effect-key ". Ignoring.")))))))

;; -- Builtin Effect Handlers  ------------------------------------------------

;; :dispatch-later
;;
;; `dispatch` one or more events after given delays. Expects a collection
;; of maps with two keys:  :`ms` and `:dispatch`
;;
;; usage:
;;
;;    {:dispatch-later [{:ms 200 :dispatch [:event-id "param"]}    ;;  in 200ms do this: (dispatch [:event-id "param"])
;;                      {:ms 100 :dispatch [:also :this :in :100ms]}]}
;;
;; TODO there is no way in Arcadia to schedule a pure fn on a thread to run ms (or any other unit) in the future. That
;; would require implementation of an event loop.
;(reg-fx
;  :dispatch-later
;  (fn [value]
;    (doseq [{:keys [ms dispatch] :as effect} value]
;        (if (or (empty? dispatch) (not (number? ms)))
;          (console :error "pastoral: ignoring bad :dispatch-later value:" effect)
;          (set-timeout! #(router/dispatch dispatch) ms)))))


;; :dispatch
;;
;; `dispatch` one event. Excepts a single vector.
;;
;; usage:
;;   {:dispatch [:event-id "param"] }

(reg-fx
  :dispatch
  (fn [value]
    (if-not (vector? value)
      (console :error "pastoral: ignoring bad :dispatch value. Expected a vector, but got:" value)
      (router/dispatch value))))


;; :dispatch-n
;;
;; `dispatch` more than one event. Expects a list or vector of events. Something for which
;; sequential? returns true.
;;
;; usage:
;;   {:dispatch-n (list [:do :all] [:three :of] [:these])}
;;
;; Note: nil events are ignored which means events can be added
;; conditionally:
;;    {:dispatch-n (list (when (> 3 5) [:conditioned-out])
;;                       [:another-one])}
;;
(reg-fx
  :dispatch-n
  (fn [value]
    (if-not (sequential? value)
      (console :error "pastoral: ignoring bad :dispatch-n value. Expected a collection, got got:" value)
      (doseq [event (remove nil? value)] (router/dispatch event)))))


;; :deregister-event-handler
;;
;; removes a previously registered event handler. Expects either a single id (
;; typically a namespaced keyword), or a seq of ids.
;;
;; usage:
;;   {:deregister-event-handler :my-id)}
;; or:
;;   {:deregister-event-handler [:one-id :another-id]}
;;
(reg-fx
  :deregister-event-handler
  (fn [value]
    (let [clear-event (partial clear-handlers events/kind)]
      (if (sequential? value)
        (doseq [event value] (clear-event event))
        (clear-event value)))))


;; :db
;;
;; reset! app-db with a new value. `value` is expected to be a map.
;;
;; usage:
;;   {:db  {:key1 value1 key2 value2}}
;;
(reg-fx
  :db
  (fn [value]
    (if-not (identical? @app-db value)
      (reset! app-db value))))

