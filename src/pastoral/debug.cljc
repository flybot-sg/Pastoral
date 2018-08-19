;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
(ns pastoral.debug)

(def debug?
  "True if the debug flag for the platform is enabled.
   - In Arcadia, true if the check box called 'Development Build' in the Unity
     Build Settings dialog is checked, or always true in the Unity Editor.
   - In ClojureCLR, if the value of the BooleanSwitch named DEBUG.
   - In Clojure, is the value of the DEBUG system property."
  #?(:clj  (System/getProperty "DEBUG")
     :cljr (try (import '[UnityEngine Debug])
                Debug/isDebugBuild
                (catch System.NullReferenceException _
                  (import '[System Diagnostics.BooleanSwitch])
                  (.-Enabled (Diagnostics.BooleanSwitch.
                               "DEBUG"
                               "Flag to disable debugging code in production builds."))))))







