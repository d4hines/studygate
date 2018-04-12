(ns fulcro-todomvc.server
  (:require [com.stuartsierra.component :as component]
            [fulcro.datomic.core :refer [build-database]]
            [fulcro.easy-server :as easy]
            [fulcro.server :as server]))

(defrecord CRMClient [config]
  ;; Implement the Lifecycle protocol
  component/Lifecycle

  (start [component*]
    (println ";; Starting CRM API Client")
    ;; In the 'start' method, initialize this component
    ;; and start it running. For example, connect to a
    ;; database, create thread pools, or initialize shared
    ;; state.
    component*)

  (stop [component*]
    (println ";; Stopping CRM API Client")
    ;; In the 'stop' method, shut down the running
    ;; component and release any external resources it has
    ;; acquired.
    ;; Return the component, optionally modified. Remember that if you
    ;; dissoc one of a record's base fields, you get a plain map.
component*))

(defn make-system
  ([] (make-system "config/dev.edn"))
  ([config-path]
   (let [config-path config-path]                           ; in production, should use a filesystem file
     (easy/make-fulcro-server
      :config-path config-path
      :parser (server/fulcro-parser)                       ; allows us to use built-in multimethods and helper macros for reads/mutates
      :parser-injections #{:config}                 ; places the named components into the env of mutations and queries
      :components {
                   :logger        {}}))))
