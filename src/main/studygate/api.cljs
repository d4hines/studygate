(ns studygate.api
  (:require [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro.util :refer [unique-key]]
            [fulcro.support-viewer :as v]
            [fulcro.client.primitives :as prim]
            [fulcro.client.logging :as log]))

;; Survey Selection
(defmutation select-survey [{:keys [id]}]
  (action [{:keys [state]}]
          (swap! state assoc :ui/selected-survey id)))

(defmutation clear-selected-survey [{:keys []}]
  (action [{:keys [state]}]
          (swap! state assoc :ui/selected-survey nil)))


;; Question Values
(defn set-question-value*
  [state-map id value]
  (assoc-in state-map [:survey-question/by-id id :question/value] value))

(defn clear-key [state-map table key]
  (let [entries (table state-map)
        cleared (reduce-kv #(assoc %1 %2 (dissoc %3 key)) {} entries)]
    (assoc state-map table cleared)))

(defn clear-question-values [state-map]
  (let [questions (:survey-question/by-id state-map)
        cleared (reduce-kv  #(assoc %1 %2 (dissoc %3 :question/value)) {} questions)]
    (assoc state-map :survey-question/by-id cleared)))

(defn clear-selected-survey [state-map]
  (let [lists (:survey-list/by-id state-map)
        cleared (reduce-kv #(assoc %1 %2 (dissoc %3 :ui/selected-survey)) {} lists)]
    (assoc state-map :survey-list/by-id cleared)))

(defmutation set-question-value [{:keys [id value]}]
  (action [{:keys [state]}]
          (swap! state set-question-value* id value)))

(defmutation reset [{:keys []}]
  (action [{:keys [state]}]
          (swap! state (fn [state-map]
                         (-> state-map
                             clear-question-values
                             clear-selected-survey)))))


;; Routing
(defn route* [state-map route]
  (assoc-in state-map [:application :root :ui/route] route))

(defmutation route-to [{:keys [route]}]
  (action [{:keys [state]}]
          (swap! state route* route)))




;; Submitting questions
(defn submit-questions*
  [state-map]
  (assoc-in state-map [:application :root :ui/route]
            :survey-finished))

(defmutation ^:intern submit-questions [{:keys [entity questions]}]
  (action [{:keys [state]}]
          (swap! state submit-questions*))
  (remote [env] true))

