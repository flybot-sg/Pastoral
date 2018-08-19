;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
(ns pastoral.string
  (:require
    [clojure.string :as string]))

(defn camel->lisp-case
  "Converts string to lisp-case."
  [s]
  (let [replacement #(str "-" (string/lower-case (second %)))]
    (-> s
        (string/replace #"^([A-Z][a-z]|[1-9A-Z])" #(string/lower-case (second %)))
        (string/replace #"([A-Z][a-z])" replacement)
        (string/replace #"([1-9A-Z]+)" replacement))))

(defn snake->lisp-case
  [s]
  (-> s
    (string/replace #"_" "-")
    (string/lower-case)))