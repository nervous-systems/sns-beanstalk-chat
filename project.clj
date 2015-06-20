(defproject io.nervous/sns-beanstalk-chat "0.1.0-SNAPSHOT"
  :license {:name "Unlicense" :url "http://unlicense.org/UNLICENSE"}
  :aot [sns-beanstalk-chat.handler]
  :main sns-beanstalk-chat.handler
  :dependencies [[org.clojure/clojure        "1.7.0-beta2"]
                 [org.clojure/core.async     "0.1.346.0-17112a-alpha"]
                 [org.clojure/tools.logging  "0.3.1"]

                 [io.nervous/fink-nottle "0.2.0-SNAPSHOT"]
                 [compojure     "1.1.5"]
                 [http-kit      "2.1.18"]
                 [cheshire      "5.5.0"]])
