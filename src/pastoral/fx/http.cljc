;; Copyright (c) 2018 Flybot Pte Ltd, Singapore.
;;
;; This file is distributed under the Eclipse Public License, the same as
;; Clojure.
(ns pastoral.fx.http
  (:require
    [clojure.string :as string]
    [flybot.console :refer [console]]
    [pastoral.coro :as coro]
    [pastoral.core :refer [dispatch]]
    [pastoral.fx :refer [reg-fx]])
  (:import [|System.Collections.Generic.List`1[UnityEngine.Network.IMultipartFormSection]|]
           [UnityEngine WWWForm]
           [UnityEngine.Networking UnityWebRequest MultipartFormDataSection]))

(defn done?
  "Return true if web request returns and payload downloads successfully,
   false if web request or playload download fails"
  [^UnityWebRequest request]
  (.. request downloadHandler isDone))

(defn error?
  "Return true if web request encounter network or http error,
   false if no error"
  [^UnityWebRequest request]
  (or (.-isNetworkError request)
      (.-isHttpError request)))

(defn error
  "Return error details after web request fails."
  [^UnityWebRequest request]
  (.-error request))

(defn text
  "Return text after request returns successfully"
  [^UnityWebRequest request]
  (.. request downloadHandler text))

;; TODO may add encode as parameter
(defn escape-url
  "Encode with UTF-8"
  [s]
  (UnityWebRequest/EscapeURL s))

(defn encode-params
  [params]
  {:pre (map? params)}
  (->> params
       (map (fn [[k v]]
              (str (escape-url (name k))
                   "="
                   (escape-url v))))
       (string/join "&")))

(defn params->form
  [params]
  {:pre (map? params)}
  (let [form (WWWForm.)]
    (doseq [[k v] params]
      (.AddField form (name k) v))
    form))

(defn build-request
  [{:keys [uri method params]}]
  (case method
    ;; assume uri don't include any query parameter
    :get (UnityWebRequest/Get (if (empty? params) uri (str uri "?" (encode-params params))))
    ;; assume post don't have any query parameters 
    :post (UnityWebRequest/Post uri (params->form params))))

;; TODO improve the response, include more information like
;; status code, last modified etc.
(defn invoke
  [{:keys [on-success on-failure] :as m-request}]
  (let [m-req (dissoc m-request :on-success :on-failure)
        request (build-request m-req)
        _ (.SendWebRequest request)]
    (fn []
      (cond
        (error? request) (do (console :error "failed to get " m-req)
                             (on-failure (error request))
                             false)
        (done? request)  (do (on-success (text request))
                             false)
        :otherwise       (do (console :log "invoke " m-req)
                             (not (done? request)))))))

;; TODO avoid conflict if user start multiple coro
(defn http-effect
  [request]
  (let [requests (if (sequential? request) request [request])]
    (coro/start
     (->> requests
          (map (fn [{:keys [on-success on-failure] :as request}]
                 (invoke (assoc request
                          :on-success #(dispatch (conj on-success %))
                          :on-failure #(dispatch (conj on-failure %))))))
          coro/together)
     (coro/default-root) :http-unity)))

(reg-fx :http-unity http-effect)

(comment

  ;; For multipart/form-data, file upload, not work with aleph for now
  (let [form (new |System.Collections.Generic.List`1[UnityEngine.Networking.IMultipartFormSection]|)]
    (doseq [[n v] params]
      (.Add form (MultipartFormDataSection. (name n) v)))
    (.Add form (MultipartFormDataSection. "field1=foo&field2=bar"))
    form))


