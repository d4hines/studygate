(ns fulcro-todomvc.api
  (:require [clojure.core :refer [pr-str]]
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

(defn resolve-ids
  "Helper function to map from Fulcro tempids through Datomic tempids down to real IDs."
  [new-db fulcroids->tempids tempids->realids]
  (reduce
   (fn [acc [cid dtmpid]]
     (assoc acc cid (d/resolve-tempid new-db tempids->realids dtmpid)))
   {}
   fulcroids->tempids))

;; (defn make-list-
  ;; "Make a new list with the given title on the given Datomic database connection.

  ;; Returns the real ID of the new list."
  ;; [connection list-name]
  ;; (let [;; id      (d/tempid :db.part/user)
  ;;       record      {:new_title list-name}
  ;;       ;; idmap   (:tempids @(d/transact connection tx))
  ;;       real-id (dyn/create-record  todolists record)
  ;;       ]
  ;;   real-id))

(defn make-list
  "Make a new list with the given title on the given Datomic database connection.

  Returns the real ID of the new list."
  [config connection list-name]
  (let [id      (d/tempid :db.part/user)
        tx      [{:db/id id :list/title list-name}]
        idmap   (:tempids @(d/transact connection tx))
        ;; Check to see if the list already exists in CRM, otherwise, create it.
        crm-id (if-let [id (->> (str "new_title eq '" list-name "'")
                                (dyn/retrieve-multiple config todolists nil)
                                first
                                :new_todolistsid)]
                 (do (log/info "Existing List " list-name " found in CRM")
                     id)
                 (do (log/info "Creating List " list-name " in CRM")
                     (dyn/create-record config todolists {:new_title list-name})))
        real-id (d/resolve-tempid (d/db connection) idmap id)
        ;; e.g. 17592186045433
        _ (log/info "Datomic ID" real-id)
    _ (log/info "CRM ID" real-id)]
    real-id))

;; (defn find-list-
;;   "Find or create a list with the given name. Always returns a valid list ID."
;;   [conn list-name]
;;   (if-let [eid (-> (dyn/retrieve-multiple todolists ["new_todolistsid"] (str "new_title eq '" list-name "'"))
;;                    first
;;                    :new_todolistsid)]
;;     eid
;;     (make-list conn list-name)))

(defn find-list
  "Find or create a list with the given name. Always returns a valid list ID."
  [config conn list-name]
  (if-let [eid (d/q '[:find ?e . :in $ ?n :where [?e :list/title ?n]] (d/db conn) list-name)]
    (do (log/info "!!!!find-list EID" (pr-str eid)) eid)
    (make-list config conn list-name)))

(defmutation todo-new-item
  [{:keys [id text list-id]}]
  (action [{:keys [todo-database ] {{:keys [crm-config]} :value} :config}]
          (let [connection         (db/get-connection todo-database) ; See fulcro-datomic for this API
                datomic-id         (d/tempid :db.part/user)       ; in order to create an entity, we need a proper datomic temp ID
                fulcroid->tempid   {id datomic-id}                ; remember that the incoming temp id (id) maps to the datomic one
          ; The Datomic list of new facts to add to the database.
                tx                 [[:db/add list-id :list/items datomic-id] {:db/id datomic-id :item/complete false :item/label text}]
                result             @(d/transact connection tx)
                tempid->realid     (:tempids result)              ; remap the incoming om tempid to the now-real datomic ID
                fulcroids->realids (resolve-ids (d/db connection) fulcroid->tempid tempid->realid)]
            (log/info "Added list item " text " to " list)
            ;; e.g. {#fulcro/tempid["99a1e4a0-eb4f-46b3-8bc9-bfc2aa974cab"] 17592186045437}
            (log/info fulcroids->realids)
            {:tempids fulcroids->realids})))

(defmutation todo-check [{:keys [id]}]
  (action [{:keys [todo-database]}]
          (let [connection (db/get-connection todo-database)
                tx         [[:db/add id :item/complete true]]] ; New datomic fact. The entity at ID is not complete.
            @(d/transact connection tx)
            (log/info "Checked list item " id)
            true)))

(defmutation todo-uncheck [{:keys [id]}]
  (action [{:keys [todo-database]}]
          (let [connection (db/get-connection todo-database)
                tx         [[:db/add id :item/complete false]]]
            @(d/transact connection tx)
            (log/info "Unchecked list item " id)
            true)))

(defmutation commit-label-change [{:keys [id text]}]
  (action [{:keys [todo-database]}]
          (let [connection (db/get-connection todo-database)
                tx         [[:db/add id :item/label text]]]
            @(d/transact connection tx)
            (log/info "Updated list item " id " to " text)
            true)))

(defn- set-checked
  [connection list-id value]
  (let [ids (d/q '[:find [?e ...] :in $ ?list-id ; find all of the entity IDs that are the join target of the given list entity's items
                   :where
                   [?list-id :list/items ?e]] (d/db connection) list-id)
        tx  (mapv (fn [id] [:db/add id :item/complete value]) ids)] ; make a tx that updates the complete fact on the all.
    @(d/transact connection tx)
    (log/info "Set all items in " list-id " to " (if value "checked" "unchecked"))
    true))

(defmutation todo-check-all [{:keys [list-id]}]
  (action [{:keys [todo-database]}]
          (let [connection (db/get-connection todo-database)] (set-checked connection list-id true))))

(defmutation todo-uncheck-all [{:keys [list-id]}]
  (action [{:keys [todo-database]}]
          (let [connection (db/get-connection todo-database)] (set-checked connection list-id false))))

(defmutation todo-delete-item [{:keys [list-id id]}]
  (action [{:keys [todo-database]}]
          (let [connection (db/get-connection todo-database)
                tx         [[:db.fn/retractEntity id]]] ; the graph edges (:list/items) self-heal in Datomic.
            @(d/transact connection tx)
            (log/info "Deleted item " id)
            true)))

(defmutation todo-clear-complete [{:keys [list-id]}]
  (action [{:keys [todo-database]}]
          (let [connection (db/get-connection todo-database)
                ids        (d/q '[:find [?e ...] :in $ ?list-id ; find all entity IDs where they are items in list-id and complete = true
                                  :where
                                  [?list-id :list/items ?e]
                                  [?e :item/complete true]] (d/db connection) list-id)
                tx         (mapv (fn [id] [:db.fn/retractEntity id]) ids)] ; make a tx that retracts them all (:list/items edges self-heal)
            @(d/transact connection tx)
            (log/info "Deleted all cleared items in list " list-id)
            true)))

(defn read-list [config connection query nm]
  (let [list-id (find-list config connection nm)
        db      (d/db connection)
        rv      (d/pull db query list-id)
        ;; {:db/id 17592186045431, :list/title "successList"}
        _       (log/info (pr-str rv))
        crm-id (:new_todolistsid
                (first (dyn/retrieve-multiple config todolists nil
                                              (str "new_title eq '" nm "'"))))
        crm-todoitems (dyn/retrieve-multiple config todoitems
                                         ["new_complete" "new_label" "new_todoitemid"]
                                         (str "_new_todolistid_value eq " crm-id))] ; Datomic's pull can handle Fulcro query syntax
    (assoc rv :new_todolistsid crm-id :crm_todoitems crm-todoitems)))

(defquery-root :todos
  "Returns the todo items for the given list."
  (value [{:keys [query todo-database] {{:keys [crm-config]} :value} :config} {:keys [list]}]
         (log/info "Responding to request for list: " list)
         (let [connection (db/get-connection todo-database)]
           (read-list crm-config connection query list))))

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
