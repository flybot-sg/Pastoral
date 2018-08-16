;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
(ns pastoral.unity.enum
  (:require
    [pastoral.unity.string :refer [snake->lisp-case]])
  (:import
    [System Enum]))

(defn val->keyword
  "Returns a keyword representation of the name of the enum-val."
  [enum-val case-fn]
  (keyword (case-fn (str enum-val))))

(defn names
  "Returns an array of the names of the constants in enumeration t."
  [^Type t]
  (Enum/GetNames t))

(defn values
  "Returns a lazy sequence of the values of the contacts in enumeration t."
  [^Type t]
  (Enum/GetValues t))

(defn pred-map
  "Returns a mapping of a keyword representation of the name of each constant in
   enumeration t to the return value of (pred enum-val). pred must be free of
   side-effects."
  [pred ^Type t]
  (reduce
    (fn [m enum-val]
      (try
        (assoc m (val->keyword enum-val snake->lisp-case)
                 (pred enum-val))
        (catch Exception _
          (assoc m (val->keyword enum-val snake->lisp-case)
                   false))))
    {}
    (values t)))