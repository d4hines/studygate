(ns studygate.intro
  (:require [devcards.core :as rc :refer-macros [defcard]]
            [fulcro.client.dom :as dom]
            [fulcro.client.cards :refer [defcard-fulcro]]
            [fulcro.client.mutations :refer [defmutation]]
            [fulcro.client.primitives :as prim :refer [defsc]]

            ))

(defmutation change-item-label [{:keys [text]}]
  (action [{:keys [state]}]
          (swap! state assoc :item/label text)))

(defmutation toggle-complete [{:keys [text]}]
  (action [{:keys [state]}]
          (swap! state update :item/complete? not)))

(defsc TodoItem [this {:keys [db/id item/label item/complete? ui/editing?]}]
  {:initial-state {:db/id 1 :item/label "Buy stuff" :item/complete false :ui/editing? true}}
  (dom/li nil (dom/input #js {:type "checkbox"
                              :onClick (fn [evt] (prim/transact! this `[(toggle-complete {})]))
                              :checked complete?})
          (if editing?
            (dom/input #js {:type "text"
                            :value label
                            :onChange (fn [evt] (prim/transact! this `[(change-item-label {:text ~(.. evt -target -value)})]))})
            label)))

(def ui-todo-item (prim/factory TodoItem))

(defcard todo-item-unchecked "A To-Do Item"
  (ui-todo-item {:db/id 1 :item/label "Buy Milk" :item/complete? false :ui/editing? false}))

(defcard todo-item-unchecked "A To-o Item"
  (ui-todo-item {:db/id 1 :item/label "Buy Milk" :item/complete? true :ui/editing? false}))

(defsc MyRoot [this {:keys [name] :as props}]
  {:initial-state {:name "Daniel"}}
  (dom/div #js {}
           (str "Hi " name)))


(defcard-fulcro active-todo-item "Hello!"
  TodoItem
  {}
  {:inspect-data true})

(defcard-fulcro my-card "# Full App"
  MyRoot
  {}
  {:inspect-data true})
