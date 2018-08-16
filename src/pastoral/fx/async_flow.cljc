;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
;;
;; This file incorporates work covered by the following copyright and
;; permission notice:
;;
;;     Copyright (c) 2016 Mike Thompson
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
(ns pastoral.fx.async-flow
  (:require
    [pastoral.core :as pastoral]
    [clojure.set :as set]))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure.
  The key thing is that 'm' remains identical? to itself if the path was never present"
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))


(defn as-callback-pred
  "Looks at the required-events items and returns a predicate which
  will either
  - match only the event-keyword if a keyword is supplied
  - match the entire event vector if a collection is supplied
  - returns a callback-pred if it is a fn"
  [callback-pred]
  (when callback-pred
    (cond (fn? callback-pred) callback-pred
          (keyword? callback-pred) (fn [[event-id _]]
                                     (= callback-pred event-id))
          (coll? callback-pred) (fn [event-v]
                                  (= callback-pred event-v))
          :else (throw
                  (ex-info (str (pr-str callback-pred)
                             " isn't an event predicate")
                    {:callback-pred callback-pred})))))

(pastoral/reg-fx
  :forward-events
  (let [id->listen-fn     (atom {})
        process-one-entry (fn [{:as m :keys [unregister register events dispatch-to]}]
                            (let [_ (assert (map? m) (str "pastoral: effects handler for :forward-events expected a map or a list of maps. Got: " m))
                                  _ (assert (or (= #{:unregister} (-> m keys set))
                                              (= #{:register :events :dispatch-to} (-> m keys set))) (str "pastoral: effects handler for :forward-events given wrong map keys" (-> m keys set)))]
                              (if unregister
                                (pastoral/remove-post-event-callback unregister)
                                (let [events-preds           (map as-callback-pred events)
                                      post-event-callback-fn (fn [event-v _]
                                                               (when (some (fn [pred] (pred event-v))
                                                                       events-preds)
                                                                 (pastoral/dispatch (conj dispatch-to event-v))))]
                                  (pastoral/add-post-event-callback register post-event-callback-fn)))))]
    (fn [val]
      (cond
        (map? val) (process-one-entry val)
        (sequential? val) (doall (map process-one-entry val))
        :else (pastoral/console :error ":forward-events expected a map or a list of maps, but got: " val)))))

(defn seen-all-of?
  [required-events seen-events]
  (let [callback-preds (map as-callback-pred required-events)]
    (every?
      (fn [pred] (some pred seen-events))
      callback-preds)))


(defn seen-any-of?
  [required-events seen-events]
  (let [callback-preds (map as-callback-pred required-events)]
    (some?
      (some
        (fn [pred] (some pred seen-events))
        callback-preds))))


(defn startable-rules
  "Given the accumulated set of seen events and the set of rules already started,
  return the list of rules which should now be started"
  [rules now-seen-events rules-fired]
  (->> (remove (comp rules-fired :id) rules)
       (filterv (fn [task] ((:when task) (:events task) now-seen-events)))))


(def map-when->fn {:seen?        seen-all-of?
                   :seen-both?   seen-all-of?
                   :seen-all-of? seen-all-of?
                   :seen-any-of? seen-any-of?})

(defn when->fn
  [when-kw]
  (if-let [when-fn (map-when->fn when-kw)]
    when-fn
    (pastoral/console :error "async-flow: got bad value for :when - " when-kw)))

(defn massage-rules
  "Massage the supplied rules as follows:
    - replace `:when` keyword value with a function implementing the predicate
    - ensure that only `:dispatch` or `:dispatch-n` is provided
    - add a unique :id, if one not already present"
  [rules]
  (->> rules
       (map-indexed (fn [index {:as rule :keys [id when events dispatch dispatch-n dispatch-fn halt?]}]
                      (if (< 1 (count (remove nil [dispatch dispatch-n dispatch-fn])))
                        (pastoral/console :error
                                          "async-flow: rule can only specify one of :dispatch, :dispatch-n or :dispatch-fn. Got more than one: " rule)
                        (cond-> {:id (or id index)
                                 :halt? (or halt? false)
                                 :when (when->fn when)
                                 :events (if (coll? events) (set events) (hash-set events))}
                          dispatch-fn (assoc :dispatch-fn dispatch-fn)
                          (not dispatch-fn) (assoc :dispatch-n (cond
                                                                 dispatch-n dispatch-n
                                                                 dispatch (list dispatch)
                                                                 :else '()))))))))

(defn- rules->dispatches
  "Given a rule and event, return a sequence of dispatches. For each dispatch in the rule:
   - if the dispatch is a keyword, return it as-is; or
   - if the dispatch if a function, return the result of calling the function with the event."
  [rules event]
  (mapcat (fn [rule]
            (let [{:keys [dispatch-fn dispatch-n]} rule]
              (cond
                dispatch-n dispatch-n
                dispatch-fn (let [dispatch-n (dispatch-fn event)]
                              (if (every? vector? dispatch-n)
                                dispatch-n
                                (pastoral/console :error
                                                  "async-flow: dispatch-fn must return a seq of events " rule)))
                :else '())))
          rules))

;; -- Event Handler

(defn make-flow-event-handler
  "Given a flow definitiion, returns an event handler which implements this definition"
  [{:keys [id db-path rules first-dispatch]}]
  (let [
        ;; Subject to db-path, state is either stored in app-db or in a local atom
        ;; Two pieces of state are maintained:
        ;;  - the set of seen events
        ;;  - the set of started tasks
        _           (assert (or (nil? db-path) (vector? db-path)) "aync-flow: db-path must be a vector")
        local-store (atom {})
        set-state   (if db-path
                      (fn [db seen started]
                        (assoc-in db db-path {:seen-events seen :rules-fired started}))
                      (fn [db seen started]
                        (reset! local-store {:seen-events seen :rules-fired started})
                        db))
        get-state   (if db-path
                      (fn [db] (get-in db db-path))
                      (fn [_] @local-store))

        rules       (massage-rules rules)]       ;; all of the events refered to in the rules

    ;; Return an event handler which will manage the flow.
    ;; This event handler will receive 3 kinds of events:
    ;;   (dispatch [:id :setup])
    ;;   (dispatch [:id :halt-flow])
    ;;   (dispatch [:id [:forwarded :event :vector]])
    ;;
    ;; This event handler returns a map of effects - it expects to be registered using
    ;; reg-event-fx
    ;;
    (fn async-flow-event-hander
      [{:keys [db]} [_ event-type :as event-v]]

      (condp = event-type
        ;; Setup this flow coordinator:
        ;;   1. Establish initial state - :seen-events and ::rules-fired are made empty sets
        ;;   2. dispatch the first event, to kick start flow
        ;;   3. arrange for the events to be forwarded to this handler
        :setup {:db             (set-state db #{} #{})
                :dispatch       first-dispatch
                :forward-events {:register    id
                                 :events      (apply set/union (map :events rules))
                                 :dispatch-to [id]}}

        ;; Here we are managing the flow.
        ;; A new event has been forwarded, so work out what should happen:
        ;;  1. does this new event mean we should dispatch another?
        ;;  2. remember this event has happened
        (let [[_ forwarded-event] event-v
              {:keys [seen-events rules-fired]} (get-state db)
              new-seen-events (conj seen-events forwarded-event)
              ready-rules     (startable-rules rules new-seen-events rules-fired)
              halt?           (some :halt? ready-rules)
              ready-rules-ids (->> ready-rules (map :id) set)
              new-rules-fired (set/union rules-fired ready-rules-ids)
              new-dispatches  (rules->dispatches ready-rules forwarded-event)
              new-db          (set-state db new-seen-events new-rules-fired)]
          (merge
           {:db new-db}
           (when (seq new-dispatches)
             {:dispatch-n new-dispatches})
           (when halt?
             ;; Teardown this flow coordinator:
             ;;   1. remove this event handler
             ;;   2. remove any state stored in app-db
             ;;   3. deregister the events forwarder
             {:db                       (dissoc-in new-db db-path)
              :forward-events           {:unregister id}
              :deregister-event-handler id})))))))


(defn- ensure-has-id
  "Ensure `flow` has an id. Return a vector of [id flow]." 
  [flow]
  (if-let [id (:id flow)]
    [id flow]
    (let [new-id (keyword (str "async-flow/" (gensym "id-")))]
      [new-id (assoc flow :id new-id)])))


;; -- Effect handler


(defn flow->handler
  "Action the given flow effect"
  [flow]
  (let [[id flow']  (ensure-has-id flow)]
    (pastoral/reg-event-fx id (make-flow-event-handler flow'))   ;; register event handler
    (pastoral/dispatch [id :setup])))                            ;; kicks things off

(pastoral/reg-fx
  :async-flow
  flow->handler)
