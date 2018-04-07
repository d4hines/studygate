(ns fulcro-todomvc.cards 
  (:require [fulcro-todomvc.ui :as ui]
            [devcards.core :as rc :refer-macros [defcard]]
            [fulcro.client.dom :as dom]
            [fulcro.client.cards :refer [defcard-fulcro]]
            [fulcro.client.mutations :refer [defmutation]]
            [fulcro.client.primitives :as prim :refer [defsc]]
            ))





(defcard-fulcro option-item
  "Option Set Item"
  ui/OptionSetQuestion
  {}
  {:inspect-data false
   :fulcro {:started-callback
            (fn [app] :question ui/OptionSetQuestion)}})

