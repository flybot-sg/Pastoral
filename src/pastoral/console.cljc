;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
;;
;; This file incorporates work covered by the following copyright and
;; permission notice:
;;
;;     Copyright (c) 2015-2017 Michael Thompson
;;
;;     Permission is hereby granted, free of charge, to any person obtaining
;;     a copy of this software and associated documentation files (the
;;     "Software"), to deal in the Software without restriction, including
;;     without limitation the rights to use, copy, modify, merge, publish,
;;     distribute, sublicense, and/or sell copies of the Software, and to
;;     permit persons to whom the Software is furnished to do so, subject to
;;     the following conditions:
;;
;;     The above copyright notice and this permission notice shall be included
;;     in all copies or substantial portions of the Software.
;;
;;     THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
;;     OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
;;     MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
;;     IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
;;     CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
;;     TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
;;     SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
(ns pastoral.console
  (:require
   [clojure.set :refer [difference]]
   [clojure.string :as string]
   #?(:clj [clojure.tools.logging :as log])))

;; This beauty is so that we can default to the Unity Editor console in Arcadia,
;; but otherwise fall back to .NET Framework for vanilla Clojure CLR.
#?(:cljr (try (import '[UnityEngine Debug])
              (catch System.NullReferenceException _
                (import '[System Console]))))

(defn write
  [level & args]
  (let [message (if (= 1 (count args))
                  (first args)
                  (string/join " " args))]
    #?(:clj (log/log level message)
       :cljr (try (case level
                    :info (Debug/Log message)
                    :warn (Debug/LogWarning message)
                    :error (Debug/LogError message))
                  (catch System.InvalidOperationException _
                    (Console/WriteLine (str (name level) ": " message)))
                  (catch System.MissingMethodException _
                    (Console/WriteLine (str (name level) ": " message)))))))

(def levels
  "Holds the current set of console functions.
   By default, pastoral.console uses the following depending on the platform:
   - Clojure uses clojure.tools.logging
   - Arcadia (Unity + ClojureCLR) uses UnityEngine.Debug
   - ClojureCLR uses System.Console"
  (atom {:log      (partial write :info)
         :warn     (partial write :warn)
         :error    (partial write :error)
         :group    (partial write :info)
         :groupEnd #()}))

(defn console
  [level & args]
  (assert (contains? @levels level)
          (str "pastoral.console/console: called with unknown level: " level))
  (apply (level @levels) args))

(defn set-fns!
  "Change the set (or a subset) of console functions.
   `new-fns` should be a map with the same keys as
   `pastoral.console/levels`."
  [new-fns]
  (assert (empty? (difference (set (keys new-fns)) (-> @levels keys set)))
          "pastoral.console/set-fns!: called with unknown keys in new-fns")
  (swap! levels merge new-fns))
 
(defn get-fns
  "Get the current console functions."
  []
  @levels)






