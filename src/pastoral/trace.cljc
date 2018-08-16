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
(ns pastoral.trace
  "Tracing for pastoral.
  Alpha quality, subject to change/break at any time."
  (:require
    [flybot.console :refer [console]]
    [pastoral.datetime :as datetime]))

(def id (atom 0))
(def ^:dynamic *current-trace* nil)

(defn reset-tracing! []
  (reset! id 0))

(def ^Boolean trace-enabled? false)

(defn ^Boolean is-trace-enabled?
  "See https://groups.google.com/d/msg/clojurescript/jk43kmYiMhA/IHglVr_TPdgJ for more details"
  []
  trace-enabled?)

(def trace-cbs (atom {}))
(defonce traces (atom []))
(defonce next-delivery (atom 0))

(defn register-trace-cb
  "Registers a tracing callback function which will receive a collection of one or more traces.
  Will replace an existing callback function if it shares the same key."
  [key f]
  (if trace-enabled?
    (swap! trace-cbs assoc key f)
    (console :warn "Tracing is not enabled. Please set {\"re_frame.trace.trace_enabled_QMARK_\" true} in :closure-defines. See: https://github.com/Day8/pastoral-trace#installation.")))

(defn remove-trace-cb [key]
  (swap! trace-cbs dissoc key)
  nil)

(defn next-id [] (swap! id inc))

(defn start-trace [{:keys [operation op-type tags child-of]}]
  {:id        (next-id)
   :operation operation
   :op-type   op-type
   :tags      tags
   :child-of  (or child-of (:id *current-trace*))
   :start     (datetime/now)})

;; On debouncing
;;
;; We debounce delivering traces to registered cbs so that
;; we can deliver them in batches. This aids us in efficiency
;; but also importantly lets us avoid slowing down the host
;; application by running any trace code in the critical path.
;;
;; We add a lightweight check on top of goog.functions/debounce
;; to avoid constant setting and cancelling of timeouts. This
;; means that we will deliver traces between 10-50 ms from the
;; last trace being created, which still achieves our goals.

(def debounce-time 50)

(defn debounce [f interval] (f))

(def schedule-debounce
  (debounce
    (fn tracing-cb-debounced []
      (doseq [[k cb] @trace-cbs]
        (try (cb @traces)
             (catch Exception e
               (console :error "Error thrown from trace cb" k "while storing" @traces e))))
      (reset! traces []))
    debounce-time))

(defn run-tracing-callbacks! [now]
  ;; Optimised debounce, we only re-debounce
  ;; if we are close to delivery time
  ;; to avoid constant setting and cancelling
  ;; timeouts.

  ;; If we are within 10 ms of next delivery
  (when (< (- @next-delivery 10) now)
    (schedule-debounce)
    ;; The next-delivery time is not perfectly accurate
    ;; as scheduling the debounce takes some time, but
    ;; it's good enough for our purposes here.
    (reset! next-delivery (+ now debounce-time))))

(defmacro finish-trace [trace]
  `(when (is-trace-enabled?)
     (let [end#      (datetime/now)
           duration# (- end# (:start ~trace))]
       (swap! traces conj (assoc ~trace
                            :duration duration#
                            :end (datetime/now)))
       (run-tracing-callbacks! end#))))

(defmacro with-trace
  "Create a trace inside the scope of the with-trace macro

       Common keys for trace-opts
       :op-type - what kind of operation is this? e.g. :render.
       :operation - identifier for the operation
       tags - a map of arbitrary kv pairs"
  [{:keys [operation op-type tags child-of] :as trace-opts} & body]
  `(if (is-trace-enabled?)
     (binding [*current-trace* (start-trace ~trace-opts)]
       (try ~@body
            (finally (finish-trace *current-trace*))))
     (do ~@body)))

(defmacro merge-trace! [m]
  ;; Overwrite keys in tags, and all top level keys.
  `(when (is-trace-enabled?)
     (let [new-trace# (-> (update *current-trace* :tags merge (:tags ~m))
                          (merge (dissoc ~m :tags)))]
       (set! *current-trace* new-trace#))
     nil))
