;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
(ns pastoral.fn-graph
  (:require
    [pastoral.kahn :as kahn])
  (:refer-clojure :exclude [compile]))

(defmacro fnk [args & body]
  `(with-meta
     (fn [{:keys ~args}]
       ~@body)
     {::requires ~(set (map keyword args))}))

(defmacro defnk [name args & body]
  `(def ~(with-meta name {::requires (set (map keyword args))}) (fnk ~args ~@body)))

(defn ->kahn [gr]
  (reduce-kv
    (fn [m k v]
      (assoc m k (-> v meta ::requires)))
    {} gr))

(defn assoc-fn [f k]
  (when f (fn [hm] (assoc hm k (f hm)))))

(defn compile [graph]
  (let [sorted-keys (kahn/sort (->kahn graph))]
    (->> sorted-keys
         (map graph)
         (map (fn [k f] (assoc-fn f k)) sorted-keys)
         (remove nil?)
         (apply comp))))
