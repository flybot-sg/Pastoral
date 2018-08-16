;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
(ns pastoral.unity.render-texture-format
  (:require
    [pastoral.unity.enum :as enum])
  (:import
    [UnityEngine SystemInfo RenderTextureFormat]))

(defn supported
  "Returns a mapping of render texture format keywords to boolean values
   indicating if the render texture format is supported on this device. Note
   that render texture format enumeration values that throw exceptions when
   passed to SystemInfo/SupportsRenderTextureFormat are false."
  []
  (enum/pred-map #(SystemInfo/SupportsRenderTextureFormat %) RenderTextureFormat))

(defn blending-supported
  "Returns a mapping of render texture format keywords to boolean values
   indicating if blending is supported on the render texture format. Note that
   render texture format enumeration values that throw exceptions when passed to
   SystemInfo/SupportsBlendingOnRenderTextureFormat are false."
  []
  (enum/pred-map #(SystemInfo/SupportsBlendingOnRenderTextureFormat %) RenderTextureFormat))