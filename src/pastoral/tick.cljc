;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
(ns pastoral.tick
  (:require
    #?(:cljr [arcadia.core :refer [object-named hook+]])
    [flybot.console :refer [console]])
  (:import
    #?(:clj [java.util.concurrent Executor Executors]
       :cljr [UnityEngine GameObject]))
  (:refer-clojure :exclude [next]))

#?(:clj
   (do
     (defonce ^:private executor (Executors/newSingleThreadExecutor))

     (defn next
       [f]
       (let [bound-f (bound-fn [& args] (apply f args))]
         (.execute ^Executor executor bound-f))
       nil))

   :cljr
   (do
     (def host (or (object-named "pastoral.tick/host")
                   (GameObject. "pastoral.tick/host")))

     (defonce fns (atom []))

     (defn on-update
       [go _]
       (run! #(try
                (%)
                (catch Exception e
                  (UnityEngine.Debug/LogException e)))
             @fns)
       (reset! fns []))

     (defn next
       [f]
       (swap! fns conj f))

     (hook+ host :update ::tick #'on-update)))