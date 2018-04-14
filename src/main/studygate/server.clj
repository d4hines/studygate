(ns studygate.server
  (:require [com.stuartsierra.component :as component]
            [fulcro.easy-server :as easy]
            [fulcro.server :as server]
            [studygate.api :as api]))

(defrecord CRMClient [config]
  ;; Implement the Lifecycle protocol
  component/Lifecycle

  (start [component*]
    (println ";; Starting CRM API Client")
    component*)

  (stop [component*]
    (println ";; Stopping CRM API Client")
    component*))

(defn make-system
  ([] (make-system "config/dev.edn"))
  ;; in production, should use a filesystem file
  ([config-path]
   (let [config-path config-path]
     (easy/make-fulcro-server
      :config-path config-path
      ;; Allows us to use built-in multimethods and helper macros for reads/mutates.
      :parser (server/fulcro-parser)
      ;; Places the named components into the env of mutations and queries.
      :parser-injections #{:config}
      :components {:logger {}}))))
