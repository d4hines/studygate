(ns studygate.cards 
  (:require [studygate.ui :as ui]
            [devcards.core :as rc :refer-macros [defcard]]
            [fulcro.client.dom :as dom]
            [fulcro.client.cards :refer [defcard-fulcro]]
            [fulcro.client.mutations :refer [defmutation]]
            [fulcro.client.primitives :as prim :refer [defsc]]
            ))

(defsc TextEntrySC [this
        {:keys [label]}]
  {:initial-state {:label "What is your favorite color?"}}
  (ui/text-input label nil))

(def ui-text-entry-sc (prim/factory TextEntrySC))

(defcard TextEntry
  "A text input."
  (ui-text-entry-sc))
