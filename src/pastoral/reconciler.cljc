;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
;;
(ns pastoral.reconciler
  (:use arcadia.core
        arcadia.linear)
  (:require
    [pastoral.console :refer [console]]
    [pastoral.animation :as anim]
    [pastoral.render :as render]
    [pastoral.router :as router]
    [pastoral.coro :as coro]
    [arcadia.internal.messages :as messages]
    [arcadia.internal.name-utils :refer [camels-to-hyphens]]
    [clojure.set :as set]
    [pastoral.namespace :as asn]
    [clojure.string :as string])
  (:import [UnityEngine Color GameObject Resources SpriteRenderer Quaternion Mathf MonoBehaviour Vector3 UI.Text]))

;; -- Object Database ---------------------------------------------------------

;; The render phase assigns ids to rendered objects and the reconciler
;; must connect those ids to Unity GameObjects. It does this using the
;; tracked-objects "database".

(def tracked-objects
  "A mapping of ids to GameObjects tracked by the reconciler"
  (atom {}))

(defn track-obj
  "Track a GameObject `obj` in the objects mapping with `id`.
  If `id` is not provided the name of the GameObject is used"
  ([obj] (track-obj obj (.name obj)))
  ([obj id]
   (if (contains? @tracked-objects id)
     (throw (ex-info "ID already tracked by reconciler" {:id id :object obj}))
     (swap! tracked-objects assoc id obj))
   obj))

(defn untrack-obj
  "Record an GameObject `obj` in the objects mapping with `id`.
  If `id` is not provided the name of the GameObject is used"
  [id]
  (swap! tracked-objects dissoc id))

(defn create-obj
  "Creates a GameObject and tracks it"
  ([] (create-obj (str (gensym "reconciler-object-"))))
  ([name] (create-obj name name))
  ([name id]
   (let [go (GameObject. name)]
     (track-obj go id))))

(defn find-obj
  "Finds the object named `identifier` tracked by the reconciler
  in the Unity scene graph."
  [identifier]
  (get @tracked-objects identifier))

;; -- Hook Attachments --------------------------------------------------------

;; Pastoral supports declarative event hooks, allowing you to invoke functions
;; and dispatch Pastoral events in response to Unity events. If a key matching
;; a Unity event name is present in a layout map, Pastoral will create the
;; necessary Arcadia hooks to register with the Unity event cycle. The keys
;; are expected to map to functions that will be invoked in response to the Unity
;; event. The signature of the function must match Arcadia conventions and will
;; take as input at the very least the GameObject and the key the hook is attached to
;; as well as any additional arguments from the Unity event.  As a convenience, a vector
;; describing a Pastoral event can be used in place of a function and that vector will
;; be dispatched in response to the Unity event.

;; The list of hook keys will change slightly with different versions of Unity
;; and Arcadia, but the current list of keys are below. They all track Unity MonoBehaviour
;; event messages, so the specifics of when and how they fire are covered in Unity's documentation.

;; Current hook keys:
;; :on-deselect :on-mouse-down :late-update :on-collision-stay :on-pointer-click
;; :on-network-instantiate :on-draw-gizmos :on-application-pause :on-destroy
;; :on-trigger-exit :on-collision-exit2d :start :on-became-visible :on-gui
;; :on-connected-to-server :on-begin-drag :on-collision-enter
;; :on-failed-to-connect-to-master-server :on-server-initialized :on-serialize-network-view
;; :on-pre-render :on-trigger-exit2d :on-render-image :on-animator-move :update
;; :on-initialize-potential-drag :on-validate :on-select :on-controller-collider-hit
;; :on-collision-exit :on-drag :on-pre-cull :on-scroll :on-trigger-enter2d :on-mouse-up-as-button
;; :on-disconnected-from-server :on-collision-enter2d :on-pointer-down :on-draw-gizmos-selected
;; :on-submit :on-mouse-over :on-enable :on-level-was-loaded :on-drop :on-pointer-exit :fixed-update
;; :on-collision-stay2d :on-render-object :on-update-selected :reset :on-post-render :on-trigger-stay2d
;; :on-trigger-enter :on-will-render-object :on-player-connected :on-animator-ik :on-became-invisible
;; :on-application-quit :on-trigger-stay :on-mouse-drag :on-joint-break :on-disable :on-mouse-exit
;; :on-cancel :on-mouse-enter :on-mouse-up :on-master-server-event :on-player-disconnected :on-move
;; :on-particle-collision :on-pointer-enter :on-failed-to-connect :on-application-focus :awake :on-end-drag
;; :on-audio-filter-read :on-pointer-up

;; Examples

;; The following layout map will create an object with the id "button"
;; and that will attach a function that logs "got mouse press" and dispatches
;; a :button-pressed event with the current time as a parameter to Pastoral
;; {:id "button"
;;  :on-mouse-up (fn [go k]
;;                 (log "got mouse press")
;;                 (dispatch [:button-pressed Time/time]))}

;; The following layout is similar but uses the convenience instead of a function.
;; Note that in this case you cannot run additional code (like logging) beyond
;; dispatching the event. 
;; {:id "button"
;;  :on-mouse-up [:button-pressed Time/time]}

(def hook-properties
  "Sequence of keywords recognized by the reconciler
  as hook keywords"
  (->> (merge messages/messages
              messages/interface-messages)
       keys
       (map #(-> % name
                 camels-to-hyphens
                 string/lower-case
                 keyword))))

(defn attach-hooks
  "Attach hooks described by `layout` to `obj`"
  [obj layout]
  (let [hook-map (select-keys layout hook-properties)]
    (reduce-kv
      (fn [_ k v]
        (arcadia.core/log "attach-hooks" obj k)
        (cond
          (vector? v) (hook+ obj k ::dispatcher
                             (fn
                               ([_] (router/dispatch v))
                               ([_ _] (router/dispatch v))
                               ([_ _ _] (router/dispatch v))))
          (ifn? v) (hook+ obj k ::dispatcher v)))
      nil
      hook-map))
  obj)

;; -- Animation Generation ----------------------------------------------------

(defn create-animations
  "Returns a sequence of animations to create objects
  in `layout`. `layout` is expected to only include new
  objects, i.e. no objects that are already in the scene."
  [layout]
  (map
    (fn [k]
      (let [v (get layout k)]
        (if-let [f (anim/get-anim-create (:type v))]
          (let [obj* (-> (:id v) create-obj (attach-hooks v))]
            (f obj* v))
          (console :warn "No creation animation for type"
                   (pr-str (:type v))
                   "key" (pr-str k)
                   "value" (pr-str v)))))
    (keys layout)))

(defn update-property-animation
  "Returns an animation to reconcile `property` of
  the object with id `id` to `value"
  [id type property value]
  (when-let [obj (find-obj id)]
    (when-let [animation-fn (anim/get-anim type property)]
      (animation-fn obj value))))

(defn update-property-animations
  "Returns a sequence of animations to reconcile the object
  with id `id` given a `properties` map."
  [id properties]
  (let [type (:type properties)]
    (reduce-kv
      (fn [v property value]
        (if-not (#{:value :id :type} property)
          (conj v (update-property-animation id type property value))
          v))
      []
      properties)))

(defn update-animations
  "Returns a sequence of animations to reconcile objects in `layout`. 
  `layout` is expected to only have objects that need to be updated,
  i.e. objects that already exist in the scene. `update-map` is expected
  to be the `:update` key from an animation map."
  [layout]
  (reduce-kv
    (fn [v id properties]
      (let [x (update-property-animations id properties)]
        (concat v x)))
    '()
    layout))

(defn generic-destroy-animation [id]
  #(destroy (object-named id)))

(defn remove-id [id]
  (let [destroy-fn (or (anim/get-anim-destroy)
                       generic-destroy-animation)]
    (untrack-obj id)
    (destroy-fn id)))

(defn animations
  "Returns a sequence of animations to reconcile `layout` map
  with the Unity scene graph. If an animation map is provided
  as the first argument, it is used to look up animation functions
  to create, update, and destroy `layout` objects of different types.
  If an animation map is not provided, default-animation-map is used."
  [layout]
  (let [current-keys (set (keys @tracked-objects))
        layout-keys (->> layout keys set)
        creation-keys (set/difference layout-keys current-keys)
        destruction-keys (set/difference current-keys layout-keys)
        creates (create-animations (select-keys layout creation-keys))
        destroys (map remove-id destruction-keys)
        updates (update-animations (apply dissoc layout creation-keys))]
    (remove nil? (concat creates updates destroys))))

;; -- Animation Loop ----------------------------------------------------------

(defonce coro-queue (atom clojure.lang.PersistentQueue/EMPTY))

(defn animation-loop []
  [#(log "animation-loop @" Time/frameCount)
   (coro/wait-while #(empty? @coro-queue))
   #(log "reconciling @" Time/frameCount "coro-queue size" (count @coro-queue))
   (coro/generate #(first @coro-queue))
   #(do (swap! coro-queue pop) false)])

(def animation-loop-cycle
  (coro/coro*
    (apply concat (repeatedly #'animation-loop))))

(defn start-loop! []
  (coro/start animation-loop-cycle)
  (let [output-keyword @render/output-keyword]
    (add-watch
      pastoral.db/app-db
      :reconcile
      (fn [k ref old-state new-state]
        (when-not (identical? new-state old-state)
          (log "enqueue @ " Time/frameCount)
          (->> new-state
               render/render
               output-keyword
               animations
               coro/together
               (swap! coro-queue conj)))))))