;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
(ns pastoral.cofx.system-info
  (:require
    [pastoral.core :as pastoral]
    [pastoral.unity.system-info :refer [system-info]]))


(pastoral/reg-cofx
  :system-info
  (fn [coeffects]
    (assoc
      coeffects
      :system-info
      (system-info))))