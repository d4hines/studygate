(ns studygate.client-setup
  (:require [studygate.ui :as ui]
            [studygate.api :as api]
            [studygate.routing :as routing]
            [fulcro.client :as fc]
            [fulcro.client.primitives :as prim]
            [goog.events :as events]
            [secretary.core :as secretary :refer-macros [defroute]]
            [goog.history.EventType :as EventType]
            [fulcro.client.mutations :refer [defmutation mutate]]
            [fulcro.client.primitives :as prim]
            [fulcro.client.data-fetch :as df])
  (:import [goog.history Html5History EventType]))

(defn on-app-started
  "Bootstraps the app with the initial data load."
  [app]
  (let [reconciler (:reconciler app)
        state      (prim/app-state reconciler)]
    (df/load app :surveys ui/SurveyList {:target [:application :root :surveys]})
    (routing/configure-routing! reconciler)
    (routing/nav! "welcome")))

;; For hot code reload.
(defonce app (atom (fc/new-fulcro-client
                    :started-callback on-app-started)))
