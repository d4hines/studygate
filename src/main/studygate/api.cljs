(ns studygate.api
  (:require [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro.util :refer [unique-key]]
            [fulcro.support-viewer :as v]
            [fipp.edn :refer [pprint]]
            [fulcro.client.primitives :as prim]
            [fulcro.client.logging :as log]))

(defn set-question-value*
  [state-map id value]
  (assoc-in state-map [:survey-question/by-id id :question/value] value))

(comment (defn on-all-questions
           "Run the xform on all of the todo items in the list with list-id. The xform will be called with the state map and the
  todo's id and must return a new state map with that todo updated. The args will be applied to the xform as additional
  arguments"
           [state-map list-id xform & args]
           (let [question-idents [(get-in state-map [:questions/by-id :db/id])]]
             (reduce (fn [s idt]
                       (let [id (second idt)]
                         (apply xform s id args))) state-map item-idents))))

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

(defmutation reset [{:keys []}]
  (action [{:keys [state]}]
          (swap! state (fn [state-map]
                         (-> state-map
                             clear-question-values
                             clear-selected-survey
                             (assoc-in [:application :root :ui/route] :survey-list))))))

(defmutation set-question-value [{:keys [id value]}]
  (action [{:keys [state]}]
          (swap! state set-question-value* id value)))

(defn submit-questions*
  [state-map]
  (assoc-in state-map [:application :root :ui/route]
            :survey-finished))

(defmutation ^:intern submit-questions [{:keys [entity questions]}]
  (action [{:keys [state]}]
          (swap! state submit-questions*))
  (remote [env] true))

