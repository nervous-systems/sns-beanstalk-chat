(ns sns-beanstalk-chat.handler
  (:require [cheshire.core :as json]
            [clojure.core.async :as async :refer [go <! >!]]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [compojure.core :as cj]
            [eulalie.creds]
            [eulalie.instance-data :as instance-data]
            [fink-nottle.sns :as sns]
            [fink-nottle.sns.consume :as sns.consume]
            [org.httpkit.client :as http.client]
            [org.httpkit.server :as http])
  (:gen-class))

(defn make-post-handler [{region :region} {chan :sns-incoming}]
  (fn [{:keys [body] :as req}]
    (go
      (let [{:keys [type] :as m} (sns.consume/stream->message body)]
        (log/info (pr-str {:event :sns-receive :data m}))
        (when (<! (sns.consume/verify-message! m region))
          (case type
            :subscription-confirmation (http.client/get (:subscribe-url m))
            :notification (async/put! chan (:message m))))))
    nil))

(defn make-get-handler [_ {mult :sns-incoming-mult out-chan :sns-outgoing}]
  (fn [req]
    (let [to-client (async/chan)]
      (async/tap mult to-client)
      (http/with-channel req handle
        (http/on-receive handle #(async/put! out-chan %))
        (async/go-loop []
          (when-let [value (<! to-client)]
            (if (http/send! handle value)
              (recur)
              (async/close! to-client))))))))

(defn make-app [config state]
  (cj/routes
   (cj/POST "/topic/events" [] (make-post-handler config state))
   (cj/GET  "/topic/events" [] (make-get-handler  config state))))

(defn sns-publish! [creds topic-arn msg-chan]
  (async/go-loop []
    (when-let [message (<! msg-chan)]
      (<! (sns/publish-topic! creds topic-arn message))
      (recur))))

(defn subscribe-sns!! [creds this-address topic-name]
  (let [topic-arn  (sns/create-topic!! creds topic-name)
        endpoint   (str "http://" this-address "/topic/events")]
    {:topic-arn topic-arn
     :subscription-arn (sns/subscribe!! creds topic-arn :http endpoint)}))

(defn get-creds!! []
  (let [iam-role (instance-data/default-iam-role!!)
        current  (atom (instance-data/iam-credentials!! iam-role))
        creds    {:eulalie/type :refresh :current current}]
    (eulalie.creds/periodically-refresh! current iam-role)
    creds))

(defn -main [& [topic]]
  (let [topic    (or topic :sns-demo-events)
        creds    (get-creds!!)
        sns-incoming (async/chan)
        sns-outgoing (async/chan)]

    (http/run-server
     (make-app
      {:topic topic :region (instance-data/identity-key!! :region)}
      {:creds creds
       :sns-incoming-mult (async/mult sns-incoming)
       :sns-incoming sns-incoming
       :sns-outgoing sns-outgoing})
     {:port 80})

    (let [{:keys [topic-arn]}
          (subscribe-sns!!
           creds (instance-data/meta-data!! :public-hostname) topic)]
      (sns-publish! creds topic-arn sns-outgoing))))
