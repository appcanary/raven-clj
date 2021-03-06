(ns raven-clj.core
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.string :as string])
  (:import [java.util Date UUID]
           [java.sql Timestamp]
           [java.net InetAddress]))

(defn- generate-uuid []
  (string/replace (UUID/randomUUID) #"-" ""))

(defn make-sentry-url [uri project-id]
  (format "%s/api/%s/store/"
          uri project-id))

(defn make-sentry-header [ts key secret]
  (format "Sentry sentry_version=2.0, sentry_client=raven-clj/1.3.1, sentry_timestamp=%s, sentry_key=%s, sentry_secret=%s"
          ts key secret))

(defn parse-dsn [dsn]
  (let [[proto-auth url] (string/split dsn #"@")
        [protocol auth] (string/split proto-auth #"://")
        [key secret] (string/split auth #":")]
    {:key key
     :secret secret
     :uri (format "%s://%s" protocol
                  (string/join
                   "/" (butlast (string/split url #"/"))))
     :project-id (Integer/parseInt (last (string/split url #"/")))}))

(defn send-packet [dsn packet-info]
  (let [{:keys [uri project-id key secret]} (parse-dsn dsn)
        ts (str (Timestamp. (.getTime (Date.))))
        url (make-sentry-url uri project-id)
        header (make-sentry-header ts key secret)]
    (http/post url
               {:throw-exceptions false
                :headers {"X-Sentry-Auth" header
                          "User-Agent" "raven-clj/1.3.1"}
                :body (json/generate-string packet-info)})))

(defn capture [dsn event-info]
  "Send a message to a Sentry server.
  event-info is a map that should contain a :message key and optional
  keys found at http://sentry.readthedocs.org/en/latest/developer/client/index.html#building-the-json-packet"
  (send-packet
   dsn
   (merge {:level "error"
           :platform "clojure"
           :server_name (.getHostName (InetAddress/getLocalHost))}
          event-info
          {:event_id (generate-uuid)})))
