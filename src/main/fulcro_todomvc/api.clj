(ns fulcro-todomvc.api
  (:require [clojure.core :refer [pr-str]]
            [clojure.pprint :refer [pprint]]
            [datomic.api :as d]
            [dynamics-clj.core :as dyn]
            [fulcro.datomic.protocols :as db]
            [fulcro.logging :as log]
            [fulcro.server :refer [defmutation defquery-root]]))

(defonce last-id (atom 1000))
(defonce requests (atom {}))
(def todolists "new_todolistses")
(def todoitems "new_todoitems")

; You can use a fully-qualified symbol with defmutation, and it will honor it. You cannot intern it though.
; This is special. Support viewer defines the client side of this. We have to define how the server receives it.
(defmutation fulcro.client.mutations/send-history
  "Server reception of a support request with history. Persists in an in-memory db for this demo."
  [p]
  (action [env]
          (let [_  (swap! last-id inc)
                id @last-id]
            (log/info "New support request " id)
            (swap! requests assoc id p)
            id)))

(defn make-list
  "Make a new list in CRM. Returns the ID of that list."
  [config connection list-name]
  (dyn/create-record config todolists {"new_title" list-name}))

(defn find-list
  "Find or create a list with the given name. Always returns a valid list ID."
  [config conn list-name]
  (if-let [id (-> (str "new_title eq '" list-name "'")
                  (partial dyn/retrieve-multiple config todolists nil)
                  first
                  (get "new_todolistsid"))]
    id
    (make-list config conn list-name)))

(defmutation todo-new-item
  [{:keys [id text list-id]}]
  (action [{:keys [todo-database] {{:keys [crm-config]} :value} :config}]
          (let [bind-id    (str "/new_todolistses(" list-id ")")
                new-record {"new_label" text
                            "new_complete" false
                            "new_TodoListId@odata.bind" bind-id}
                crm-result (dyn/create-record crm-config "new_todoitems"
                                              new-record)]
            (log/info "Added list item " text " to " list-id)
            {:tempids {id crm-result}})))

(defmutation todo-check [{:keys [id]}]
  (action [{:keys [todo-database] {{:keys [crm-config]} :value} :config}]
          (dyn/update-record crm-config todoitems id {"new_complete" true})
          (log/info "Checked list item " id)
          true))

(defmutation todo-uncheck [{:keys [id]}]
  (action [{:keys [todo-database] {{:keys [crm-config]} :value} :config}]
          (dyn/update-record crm-config todoitems id {"new_complete" false})
          (log/info "Unchecked list item " id)
          true))

(defmutation commit-label-change [{:keys [id text]}]
  (action [{:keys [todo-database] {{:keys [crm-config]} :value} :config}]
          (dyn/update-record crm-config todoitems id {"new_label" text})
          (log/info "Updated list item " id " to " text)))

(defn- set-checked
  [config list-id value]
  (doseq [id (map (fn [x] (get x "new_todoitemid"))
                  (dyn/retrieve-multiple config todoitems
                                         nil
                                         (str "_new_todolistid_value eq " list-id)))]
    (dyn/update-record config todoitems id {"new_complete" value}))
  (log/info "Set all items in " list-id " to " (if value "checked" "unchecked"))
  true)

(defmutation todo-check-all [{:keys [list-id]}]
  (action [{:keys [todo-database] {{:keys [crm-config]} :value} :config}]
          (set-checked crm-config list-id true)))

(defmutation todo-uncheck-all [{:keys [list-id]}]
  (action [{:keys [todo-database] {{:keys [crm-config]} :value} :config}]
          (set-checked crm-config list-id false)))

(defmutation todo-delete-item [{:keys [list-id id]}]
  (action [{:keys [todo-database] {{:keys [crm-config]} :value} :config}]
          (dyn/delete-record crm-config todoitems id)
          (log/info "Deleted item " id)
          true))

(defmutation todo-clear-complete [{:keys [list-id]}]
  (action [{:keys [todo-database] {{:keys [crm-config]} :value} :config}]
          (doseq [id (map (fn [x] (get x "new_todoitemid"))
                          (dyn/retrieve-multiple crm-config todoitems
                                                 nil
                                                 (str "_new_todolistid_value eq "
                                                      list-id
                                                      " and  new_complete eq true ")))]
            (dyn/delete-record crm-config todoitems id))
          (log/info "Deleted all cleared items in list " list-id)
          true))

(defn read-list [config connection query nm]
  (let [;; {:db/id 17592186045431, :list/title "successList"}
        crm-todolist (first (dyn/retrieve-multiple config todolists nil
                                                   (str "new_title eq '" nm "'")))
        crm-todolistid (get crm-todolist "new_todolistsid")
        crm-todoitems (dyn/retrieve-multiple config todoitems
                                             ["new_complete" "new_label" "new_todoitemid"]
                                             (str "_new_todolistid_value eq " crm-todolistid))
        mapped-items (mapv (fn [x] {:db/id (get x "new_todoitemid")
                                    :item/label (get x "new_label")
                                    :item/complete (get x "new_complete")}) crm-todoitems)]

    {:db/id crm-todolistid
     :list/title nm
     :list/items mapped-items}))

(defquery-root :todos
  "Returns the todo items for the given list."
  (value [{:keys [query todo-database] {{:keys [crm-config]} :value} :config} {:keys [list]}]
         (log/info "Responding to request for list: " list)
         (let [connection (db/get-connection todo-database)
               results (read-list crm-config connection query list)
               _ (pprint results)]
           results)))

(defquery-root :surveys
  "Testing"
  (value [{:keys [query]} {:keys [not-sure]}]
         {:db/id "SURVEY_ROOT"
          :survey-list/surveys [{:db/id "someguid"
                                 :survey/title "Server Side!"
                                 :survey/questions [{:db/id "qeustion-guid"
                                                     :question/displayname "Have you heard any good riddles lately?"}]}
                                {:db/id "someotherguid"
                                 :survey/title "Another Server Side!"
                                 :survey/questions [{:db/id "another-guid"
                                                     :question/displayname "What's in my pocket?"}]}]}))

(defn ensure-integer [n]
  (cond
    (string? n) (Integer/parseInt n)
    :else n))

(defquery-root :support-request
  "Get a support request by server ID (see server logs (NOT CLIENT Tx ID). This is required for the support viewer
  to work. You simply return the EDN that you saved earlier for the given support request."
  (value [env {:keys [id]}]
         (let [id      (ensure-integer id)
               history (get @requests id [])]
           (log/info "Request for client history: " id)
           (when-not (seq history)
             (log/error "Invalid history ID! Perhaps you used a client tx id instead? Known IDs are: " (pr-str (keys @requests))))
           history)))
