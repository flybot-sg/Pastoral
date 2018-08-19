;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
(ns pastoral.cofx.system-info
  (:require
    [pastoral.core :as pastoral]
    [pastoral.enum :as enum]
    [pastoral.string :refer [camel->lisp-case]])
  (:import
   [UnityEngine SystemInfo TextureFormat RenderTextureFormat]))

(defn texture-formats
  "Returns a mapping of texture format names to boolean values indicating if the
   texture format is supported on this device. Note that texture format
   enumeration values that throw exceptions when passed to
   SystemInfo/SupportsTextureFormat, namely :pvrtc_2bpp_rgb, are always false."
  []
  (enum/pred-map #(SystemInfo/SupportsTextureFormat %) TextureFormat))

(defn render-texture-formats
  "Returns a mapping of render texture format keywords to boolean values
   indicating if the render texture format is supported on this device. Note
   that render texture format enumeration values that throw exceptions when
   passed to SystemInfo/SupportsRenderTextureFormat are false."
  []
  (enum/pred-map #(SystemInfo/SupportsRenderTextureFormat %) RenderTextureFormat))

(defn blending-on-render-texture-formats
  "Returns a mapping of render texture format keywords to boolean values
   indicating if blending is supported on the render texture format. Note that
   render texture format enumeration values that throw exceptions when passed to
   SystemInfo/SupportsBlendingOnRenderTextureFormat are false."
  []
  (enum/pred-map #(SystemInfo/SupportsBlendingOnRenderTextureFormat %) RenderTextureFormat))

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
    (assoc :texture-formats (texture-formats))
    (assoc :render-texture-formats (render-texture-formats))
    (assoc :blending-on-render-texture-formats (blending-on-render-texture-formats))))

(pastoral/reg-cofx
  :system-info
  (fn [coeffects]
    (assoc
      coeffects
      :system-info
      (system-info))))




