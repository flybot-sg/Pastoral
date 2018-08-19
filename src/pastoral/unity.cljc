;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
(ns pastoral.unity
  (:require
    [clojure.string :as string]
    [clojure.reflect :refer [typename]])
  (:import
    [UnityEditor Help]
    [UnityEditorInternal InternalEditorUtility]))

(defn browse-url
  [url]
  (UnityEditor.Help/BrowseURL url))

(defn version
  []
  (let [v (UnityEditorInternal.InternalEditorUtility/GetUnityVersion)]
    (str (.-Major v) "." (.-Minor v))))

(defn url
  [^Type t]
  (str "https://docs.unity3d.com/"
       (version)
       "/Documentation/ScriptReference/"
       (string/replace (typename t) #"(UnityEngine\.|UnityEditor\.)" "")
       ".html"))

(defn unitydoc
  [^Type t]
  (browse-url (url t)))

