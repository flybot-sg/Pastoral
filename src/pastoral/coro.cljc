;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
(ns pastoral.coro
  "Implementation of stateful Coroutines used to model
  animations and other time-based logic.
  
  Coroutines wrap clojure functions and keep track of
  whether or not the wrapped functions are 'finished'.
  Coroutines implement IFn and can be treated like clojure
  functions themselves. Invoking a Coroutine invokes its wrapped
  functions, though different Coroutine types will invoke
  their wrapped functions in different ways. The wrapped functions
  take no arguments and presumably read from and write to mutable
  state or the scene graph. If a wrapped function returns a
  falsey value, it signals that it is 'finished' and if it
  returns a truthy value it signals that it is 'not finished'.
  Invoking a Coroutine repeatedly will invoke its wrapped functions
  until it signals that it is 'finished', after which invoking
  the Coroutine will be a no-op.
  "
  (:refer-clojure :exclude [sequence])
  (:use arcadia.core)
  (:import [Pastoral CoroRoot CoroGenerate CoroSingle CoroArray CoroTogether CoroSequence Coroutine]
           [UnityEngine GameObject]
           [clojure.lang IFn]))

(defn coroutine?
  "Does a value implement the Coroutine interface?"
  [x]
  (isa? (type x) Coroutine))

(defn coro*
  "Wraps a sequence of functions in a coroutine.
  
  When this coroutine is invoked, it will invoke the
  \"current\" function in the sequence, initially the
  first function in the sequence. If that function returns
  falsey, it signals that it is \"finished\" and the coroutine
  will update its \"current\" function to be the next function
  in the sequence. The coroutine itself is finished when there
  are no more functions in the sequence to invoke. In the case
  of an infinite sequence, this may never happen."
  [fns]
  (if (seq? fns)
    (CoroSequence. fns)
    (CoroArray. (into-array IFn fns))))

(defn coro
  "Wraps arguments in a coroutine.
  
  A variadic wrapper for coro*."
  [& args]
  (cond 
    (empty? args) (CoroSingle. (constantly false))
    (empty? (drop 1 args)) (CoroSingle. (first args))
    (empty? (drop 20 args)) (CoroArray. (into-array IFn args))
    :else (coro* args)))

(defn together
  "Wraps a sequence of functions in a \"together coroutine\".
  
  When a together coroutine is invoked, it will invoke every function
  in the sequence it wraps. If every function in the sequence returns
  falsey, then the together coroutine is \"finished\" and future invocations
  will not invoke the wrapped functions. If any function in the sequence
  returns truthy, then the together coroutine is \"not finished\" and future
  invocations will once again invoke the wrapped sequence of functions."  
  [fs]
  (CoroTogether. (into-array IFn fs)))

(defn generate
  "Wraps a coroutine generator function in a coroutine.
  
  When this coroutine is invoked it will invoke the wrapped function f,
  which is expected to return a coroutine. This returned coroutine
  will be invoked whenever this coroutine is invoked in the future."
  [f]
  (CoroGenerate. f))

(def default-root-name "coro-root")

(defn default-root []
  (if-let [gobj (object-named default-root-name)]
    (cmpt gobj CoroRoot)
    (let [gobj (GameObject. default-root-name)]
      (cmpt+ gobj CoroRoot))))

(defn start
  ([c] (.Begin (default-root) (coro c)))
  ([c root] (.Begin root (coro c)))
  ([c root key] (.Begin root (coro c) key)))

(defn wait-seconds [t]
  (let [time (volatile! t)]
    #(pos? (vswap! time - UnityEngine.Time/deltaTime))))

(defn wait-frames [f]
  (let [frames (volatile! f)]
    #(pos? (vswap! frames dec))))

(defn wait-while [pred?]
  (coro pred?))

(defn wait-until [pred?]
  (coro #(not (pred?))))