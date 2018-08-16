;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
(ns pastoral.unity.texture-format
  (:require
    [pastoral.unity.enum :as enum])
  (:import
    [UnityEngine SystemInfo TextureFormat]))

(defn supported
  "Returns a mapping of texture format names to boolean values indicating if the
   texture format is supported on this device. Note that texture format
   enumeration values that throw exceptions when passed to
   SystemInfo/SupportsTextureFormat, namely :pvrtc_2bpp_rgb, are always false."
  []
  (enum/pred-map #(SystemInfo/SupportsTextureFormat %) TextureFormat))
