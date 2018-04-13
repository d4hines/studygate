(ns studygate.client-setup
  (:require [fulcro.client :as fc]
            [studygate.ui :as ui]
            [studygate.api :as m]                      ; ensures mutations are loaded
            [fulcro.client.primitives :as prim]
            [fulcro.client.data-fetch :as df]))

(defn on-app-started
  "Stuff we do on initial load. Given to new-fulcro-client."
  [app]
  (let [reconciler (:reconciler app)
        state      (prim/app-state reconciler)]
        (df/load app :surveys ui/SurveyList {:target [:application :root :surveys]})))
; the defonce is so we get hot code reload
(defonce app (atom (fc/new-fulcro-client
                    :started-callback on-app-started)))
