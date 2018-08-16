;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
(ns pastoral.datetime)

(defn now
  "Returns the number of milliseconds that have elapsed since the Unix epoch
   (1970-01-01T00:00:00.000Z)."
  []
  #?(:clj (System/currentTimeMillis)
     :cljr (.ToUnixTimeMilliseconds (DateTimeOffset. DateTime/UtcNow))))

