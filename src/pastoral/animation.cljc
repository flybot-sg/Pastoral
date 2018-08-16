;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
(ns pastoral.animation
  (:use arcadia.core)
  (:require
    [flybot.registrar :refer [register-kind get-value register-value clear-values]]
    [pastoral.coro :as coro])
  (:import [UnityEngine SpriteRenderer Color Vector3 Quaternion UI.Text]))

(def kind :animation)

(register-kind kind)

(defn reg-anim
  "Register the given animation generator `handler` for the given `property` of the given layout `type`.

  `type` and `property` are keywords
  `handler` is a function which takes a Unity GameObject and the value of property from the layout
  and returns a coroutine that animates the GameObject to reflect the new value of the property

  Example Use
  -----------

  (reg-anim
     :card :position
     (fn [obj new-position]
        (pastoral.animation/move obj new-position)))"
  [type property handler]
  (register-value kind [type property] handler))

(defn reg-anim-create
  "Register the given animation generator `handler` to create an object of the given `type`.

  `type` is a keyword
  `handler` is a function which takes a layout map describing the new object and returns a
  coroutine that creates a new GameObject reflecting the value in the layout map

  Example Use
  -----------

  (reg-anim-create
    :card
    (fn [{:keys [name position value]}]
      (coro/coro
        #(let [obj (GameObject. name)
               sr (cmpt+ obj SpriteRenderer)]
           (set! (.. obj transform position) position)
           (set! (.color sr) (Color. 1 1 1 0))
           (set! (.sprite sr) (sprite-from-value value))
           false))))
  "
  [type handler]
  (register-value kind [type ::create] handler))

(defn reg-anim-destroy
  "Register the given animation generator `handler` to remove objects from the scene.

  `handler` is a function which takes a GameObject and returns a coroutine that
  removes it from the scene.

  Destroy animations are currently global and cannot dispatch on layout type, though
  this may change in the future.

  Example Use
  -----------

  (reg-anim-destroy
    :card
    (fn [obj]
      (coro/coro
        #(destroy obj))))
  "
  [handler]
  (register-value kind ::destroy handler))

(defn get-anim [type property]
  (or (get-value kind [type property])
      (get-value kind [:pastoral.core/any property])))

(defn get-anim-create [type]
  (get-value kind [type ::create]))

(defn get-anim-destroy []
  (get-value kind ::destroy))


;; -- Utilities ---------------------------------------------------------------

(defn timefn
  "Wraps a 1-arity function `f` in a 0-arity function that
  will pass the 1-arity function a value that increases
  from 0 to 1 at the rate `speed`"
  ([f] (timefn 1 f))
  ([speed f]
   (let [t (volatile! 0)]
     (fn []
       (f @t)
       (< (vswap! t + (* speed Time/deltaTime))
          1)))))


;; -- Animation Building Blocks -----------------------------------------------

;; TODO is this the right namespace for these?

(defn swap-text 
  "Returns an animation to immediately replace
  the text of `obj` with `new-text`"
  [obj new-text]
  (coro/coro
    #(let [text-cmpt (.GetComponentInChildren obj Text)]
       (set! (.text text-cmpt) new-text)
       false)))

(defn fade-out
  "Returns an animation to fade `obj`'s alpha value to
  0.0 over `seconds`. If `seconds` is absent, it defaults to 1."
  ([obj] (fade-out obj 1))
  ([obj seconds]
   (let [t (volatile! 1)]
     (fn []
       (let [^SpriteRenderer sr (cmpt obj SpriteRenderer)
             c (Color. 1 1 1 @t)]
         (set! (.. sr color) c)
         (vswap! t - (* seconds Time/deltaTime))
         (> @t 0))))))

(defn fade-in
  "Returns an animation to fade `obj`'s alpha value to
  1.0 over `seconds`. If `seconds` is absent, it defaults to 1."
  ([obj] (fade-in obj 1))
  ([obj seconds]
   (let [t (volatile! 0)]
     (fn []
       (let [^SpriteRenderer sr (cmpt obj SpriteRenderer)
             c (Color. 1 1 1 @t)]
         (set! (.. sr color) c)
         (vswap! t + (* seconds Time/deltaTime))
         (< @t 1))))))

(defn fade-to [obj v]
  "Returns an animation to immediately set `obj`'s alpha value to `v`"
  (fn []
    (let [^SpriteRenderer sr (cmpt obj SpriteRenderer)
          c (Color. 1 1 1 v)]
      (set! (.. sr color) c)
      false)))

;; animations
(defn move-to
  "Returns an animation to move `obj` towards the Vector3
  position `to` over `seconds`."
  [obj to seconds]
  (let [transform (.. obj transform)
        from (volatile! nil)
        t (volatile! 0)]
    (fn []
      (when (nil? @from)
        (vreset! from (.. transform localPosition)))
      (set! (.. transform localPosition)
            (Vector3/Lerp @from to @t))
      (vreset! t (+ @t (* seconds Time/deltaTime)))
      (> (Vector3/Distance (.. obj transform localPosition) to)
         0.1))))

(defn go-to
  "Returns an animation to immediately move `obj`
  to the Vector3 position `to`."
  [obj to]
  (fn []
    (set! (.. obj transform localPosition) to)
    false))

(defn move
  "Returns an animation to move `obj` to the Vector3 position `to`
  over `seconds`. If `seconds` is absent it defaults to 2. `obj`'s
  local position is guaranteed to equal `to` once the animation
  is finished."
  ([obj to] (move obj to 3))
  ([obj to speed]
   (coro/coro (move-to obj to speed)
              (go-to obj to))))

(defn scale-to
  "Returns an animation to scale `obj` towards the Vector3
  scale `to` over `seconds`."
  [obj to seconds]
  (let [transform (.. obj transform)
        from (volatile! nil)
        t (volatile! 0)]
    (fn []
      (when (nil? @from)
        (vreset! from (.. transform localScale)))
      (set! (.. transform localScale)
            (Vector3/Lerp @from to @t))
      (vreset! t (+ @t (* seconds Time/deltaTime)))
      (> (Mathf/Abs (- (.. obj transform localScale magnitude)
                       (.. to magnitude)))
         (* seconds Time/deltaTime)))))

(defn scale-to-now
  "Returns an animation to immediately scale `obj`
  to the Vector3 scale `to`."
  [obj to]
  (fn []
    (set! (.. obj transform localScale) to)
    false))

(defn scale
  "Returns an animation to scale `obj` to the Vector3
  scale `to` over `seconds`. If `seconds` is absent it defaults to 3.
  `obj`'s local scale is guaranteed to equal `to` once the animation
  is finished."
  ([obj to] (scale obj to 3))
  ([obj to seconds]
   (coro/coro (scale-to obj to seconds)
              (scale-to-now obj to))))

(defn rotate-to
  "Returns an animation to rotate `obj` towards the Quaternion
  `to` over `seconds`."
  [obj to seconds]
  (let [transform (.. obj transform)
        from (volatile! nil)
        t (volatile! 0)]
    (fn []
      (when (nil? @from)
        (vreset! from (.. transform localRotation)))
      (set! (.. transform localRotation)
            (Quaternion/Slerp @from to @t))
      (vswap! t + (* seconds Time/deltaTime))
      (< @t 1))))

(defn rotate-to-now
  "Returns an animation that immediately sets the
  local rotation of `obj` to the Quaternion `to`."
  [obj to]
  (fn []
    (set! (.. obj transform localRotation) to)
    false))

(defn rotate
  "Returns an animation to rotate `obj` to the Quaternion
  `to` over `seconds`. If `seconds` is absent it defaults to 3.
  `obj`'s local rotation is guaranteed to equal `to` once the animation
  is finished."
  ([obj to] (rotate obj to 3))
  ([obj to seconds]
   (coro/coro
     (rotate-to obj to seconds)
     (rotate-to-now obj to))))