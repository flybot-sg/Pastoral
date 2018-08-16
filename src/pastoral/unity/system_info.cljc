;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
(ns pastoral.unity.system-info
  (:require
    [pastoral.unity.string :refer [camel->lisp-case]]
    [pastoral.unity.texture-format :as texture-format]
    [pastoral.unity.render-texture-format :as render-texture-format]
    [pastoral.unity.enum :as enum])
  (:import
    [UnityEngine SystemInfo]))

(defn system-info
  []
  (->
    (reduce
      (fn [m prop]
        (let [value (.GetValue prop SystemInfo nil)]
          (assoc m (keyword (camel->lisp-case (.-Name prop)))
                   (if (enum? value)
                     (enum/val->keyword value camel->lisp-case)
                     value))))
      {}
      (.GetProperties SystemInfo))
    (assoc :texture-formats (texture-format/supported))
    (assoc :render-texture-formats (render-texture-format/supported))
    (assoc :blending-on-render-texture-formats (render-texture-format/blending-supported))))