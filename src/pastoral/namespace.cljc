(ns pastoral.namespace
  (:refer-clojure :exclude [alias]))

(defn alias [& alias-ns-syms]
  (doseq [[alias-sym ns-sym] (partition 2 alias-ns-syms)]
    (clojure.core/alias alias-sym (ns-name (create-ns ns-sym)))))
