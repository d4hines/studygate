(ns studygate.routing
  (:require [studygate.api :as m]
            [goog.events :as events]
            [secretary.core :as secretary :refer-macros [defroute]]
            [goog.history.EventType :as EventType]
            [fulcro.client.primitives :as prim])
  (:import [goog.history Html5History EventType]))

(defn get-token []
  (str js/window.location.pathname js/window.location.search))

(defn make-history []
  (doto (Html5History.)
    (.setPathPrefix (str js/window.location.protocol
                         "//"
                         js/window.location.host))
    (.setUseFragment false)))


(defn handle-url-change [e]
  (secretary/dispatch! js/window.location.hash))

(defonce history (doto (make-history)
                   (goog.events/listen EventType.NAVIGATE
                                       ;; wrap in a fn to allow live reloading
                                       #(handle-url-change %))
                   (.setEnabled true)))

(defn nav! [token]
  (.setToken history (str "/#" token)))

;; OPTIMIZE Transacting on the reconciler definitely the simplest method,
;; but it's also the most inefficient, causing a root-level rerender
;; (React still makes this pretty fast, but as the app grows it will
;; casue problems). This really needs to combined with Fulcro UI Routing.
(defn configure-routing! [reconciler]
  (secretary/set-config! :prefix "#")

  (defroute welcome "/welcome" []
    (prim/transact! reconciler `[(m/route-to {:route :welcome})]))

  (defroute surveys "/surveys" []
    (prim/transact! reconciler `[(m/route-to {:route :survey-list})
                                 (m/clear-selected-survey)]))

  (defroute surveyview "/surveys/:id" [id]
    (.log js/console "Found id " id)
    (prim/transact! reconciler `[(m/select-survey {:id ~id})]))

  (defroute default "*" []
    (prim/transact! reconciler `[(m/route-to {:route :welcome})])))


