(ns sns-beanstalk-chat.handler
  (:require [compojure.core :as cj]
            [org.httpkit.server :as http]
            [clojure.tools.logging :as log]
            [org.httpkit.client :as http.client]
            [clojure.core.async :as async :refer [go <! >!]]
            [fink-nottle.sns :as sns]
            [eulalie.instance-data :as instance-data]
            [eulalie.creds]
            [cheshire.core :as json])
  (:gen-class))

(defmulti handle-sns-request
  (fn [{{:keys [x-amz-sns-message-type]} :headers}]
    x-amz-sns-message-type))
(defmethod handle-sns-request "SubscriptionConfirmation" [{:keys [body]}]
  (-> body :SubscribeURL http.client/get))
(defmethod handle-sns-request "Message" [{:keys [body topic-chan]}]
  (async/put! topic-chan (:Message body)))

(defn handle-topic-post [req]
  (-> req
      (update :body json/decode true)
      handle-sns-request))

(defn handle-topic-get [{:keys [topic-mult] :as req}]
  (let [out-chan (async/chan)]
    (async/tap topic-mult out-chan)
    (http/with-channel req handle
      (async/go-loop []
        (when-let [value (<! out-chan)]
          (when (http/send! handle value)
            (recur)))))))

(defn topic-middleware [handler topic mult]
  (fn [req] (handler (assoc req
                            :topic-chan topic
                            :topic-mult mult))))

(defn make-internal-app [channel]
  (->
   (cj/routes
    (cj/POST "/topic/events" [] handle-topic-post))
   (topic-middleware channel (async/mult channel))))

(defn subscribe-sns!! [creds this-address topic-name]
  (let [topic-arn  (sns/create-topic!! creds topic-name)
        endpoint   (str "http://" this-address "/topic/events")
        subs-arn   (sns/subscribe!! creds topic-arn :http endpoint)]
    (log/info (pr-str {:event :subscribe-sns
                       :data [topic-arn endpoint subs-arn]}))
    subs-arn))

(defn -main [& [topic]]
  (let [topic    (or topic :sns-demo-events)
        iam-role (instance-data/default-iam-role!!)
        current  (atom {})
        creds    {:eulalie/type :sessioned :current current}]
    (eulalie.creds/periodically-refresh! current iam-role)
    (let [internal-ip (instance-data/retrieve!! :local-ipv4)
          events      (async/chan)]
      (http/run-server (make-internal-app events)
                       {:port 8080 :ip internal-ip})
      (subscribe-sns!! creds internal-ip topic))))