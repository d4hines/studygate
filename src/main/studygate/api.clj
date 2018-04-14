(ns studygate.api
  (:require [dynamics-clj.core :as dyn]
            [fulcro.logging :as log]
            [fulcro.server :refer [defmutation defquery-root]]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]))

(defmutation submit-questions
  "Creates an entity record in CRM corresponding to the submitted survey"
  [{:keys [entity questions]}]
   (action [{:keys [config]}]
          (log/info "Received SUBMIT event for " entity)
          (dyn/create-record (:value config) entity
                             (reduce (fn [prev {:keys [question/logicalname question/value]}]
                                       (if (not (nil? value))
                                         (assoc prev logicalname value)
                                         prev)) {} questions))
          (log/info "Record created")))

(defn parse-int [s]
  (Integer. (re-find  #"\d+" s)))

(defn label*
  "A helper method for extraction labels as returned from CRM."
  [label] (get-in label ["UserLocalizedLabel" "Label"]))

(defn get-normal-attributes
  "Retrieves all attribute metadata except that belonging to boolean and
  option set fields, which are at a different endpoint."
  [config id]
  (log/info "Retrieving normal attributes for MetadataId" id)
  (->> (get-in (dyn/retrieve* config
                              (str "EntityDefinitions(" id
                                   ")?$select=LogicalName"
                                   "&$expand=Attributes"
                                   "($select=IsPrimaryId,LogicalName,DisplayName,AttributeType,Description)"))
               [:body "Attributes"])
       (filterv (fn [{:strs [IsPrimaryId AttributeType LogicalName]}]
                  (and (str/starts-with? LogicalName "survey_")
                       (not (contains? #{"Picklist" "Boolean" "Virtual"} AttributeType))
                       (not IsPrimaryId))))
       (mapv (fn [{:strs [MetadataId LogicalName DisplayName Description] :as props}]
               {:db/id MetadataId
                :question/logicalname LogicalName
                :question/displayname (label* DisplayName)
                :question/order (parse-int (label* Description))
                ;; TODO expand to more types (int, date, etc). String is the only supported one right now.
                :question/type :text}))))

(defn options* [x] (mapv (fn [{:strs [Value Label] :as x}]
                           {:opt-value (case Value 0 false 1 true Value)
                            :opt-label (label* Label)}) x))

(defn get-boolean-attributes [config id]
  (log/info "Retrieving boolean attributes for MetadataId " id)
  (->> (get-in (dyn/retrieve* config
                              (str "EntityDefinitions(" id
                                   ")/Attributes/Microsoft.Dynamics.CRM.BooleanAttributeMetadata"
                                   "?$select=LogicalName,DisplayName,Description"
                                   "&$expand=OptionSet($select=TrueOption,FalseOption)"))
               [:body "value"])
       (mapv (fn [{:strs [MetadataId DisplayName LogicalName Description]
                   {:strs [TrueOption FalseOption]} "OptionSet"}]
               {:db/id MetadataId
                :question/logicalname LogicalName
                :question/displayname (label* DisplayName)
                :question/order (parse-int (label* Description))
                :question/type :option
                :question/options (options* [TrueOption FalseOption])}))))

(defn get-picklist-attributes [config id]
  (log/info "Retrieving picklist attributes for MetadatId " id)
  (->> (get-in (dyn/retrieve* config
                              (str "EntityDefinitions(" id
                                   ")/Attributes/Microsoft.Dynamics.CRM.PicklistAttributeMetadata"
                                   "?$select=LogicalName,DisplayName,Description"
                                   "&$expand=OptionSet($select=Options)"))
               [:body "value"])
       (mapv (fn [{:strs [MetadataId DisplayName LogicalName Description]
                   {:strs [Options]} "OptionSet"}]
               {:db/id MetadataId
                :question/logicalname LogicalName
                :question/displayname (label* DisplayName)
                :question/order (parse-int (label* Description))
                :question/type :option
                :question/options (options* Options)}))))

(defn get-entity-questions [config id]
  (into [] cat
        [(get-normal-attributes config id)
         (get-boolean-attributes config id)
         (get-picklist-attributes config id)]))

(defn get-surveys
  "Gets all CRM entities with made by the \"survey_\" publisher, and
  transforms them into the shape needed by the UI."
  [config]
  (log/info "Responding to root :survey query")
  (->> (dyn/retrieve-multiple config "EntityDefinitions" ["DisplayName" "EntitySetName" "Description"] nil)
       (filter (fn [x] (str/starts-with? (get x "EntitySetName") "survey_")))
       (mapv (fn [{:strs [EntitySetName MetadataId DisplayName Description]}]
               {:db/id MetadataId
                :survey/title (label* DisplayName)
                :survey/entity EntitySetName
                :survey/image (label* Description)
                :survey/questions (get-entity-questions config MetadataId)}))))

(defquery-root :surveys
  "Returns the data needed by the whole UI tree.
  TODO This isn't really the \"Fulcro way\". It would be better to create a parser that can
  instead return the various pieces as they're asked for. Nevertheless, for such a small app.
  this will do fine for now."
  (value [{:keys [query] {:keys [value]} :config} {:keys [list]}]
         {:db/id (:crmorg value)
          :survey-list/surveys (get-surveys value)}))

