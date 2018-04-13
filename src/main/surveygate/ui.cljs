(ns studygate.ui
  (:require [fulcro.client.primitives :as prim :refer [defsc]]
            [fulcro.client.mutations :as mut :refer [defmutation]]
            [studygate.api :as api]
            [fulcro.i18n :refer [tr trf]]
            yahoo.intl-messageformat-with-locales
            [fulcro.client.alpha.dom :as dom]
            [fulcro.client :as fc]))

(defn is-enter? [evt] (= 13 (.-keyCode evt)))
(defn is-escape? [evt] (= 27 (.-keyCode evt)))

(defn trim-text [text]
  "Returns text without surrounding whitespace if not empty, otherwise nil"
  (let [trimmed-text (clojure.string/trim text)]
    (when-not (empty? trimmed-text)
      trimmed-text)))

(defn string-input [component callback {:keys [db/id question/displayname question/options question/value]}]
  (dom/div #js {:className "question"}
           (dom/input #js {:className "effect-17"
                           :type "text"
                           :value     (or value "")
                           :onChange #(let [val (-> % .-target .-value)]
                                        (callback val))})
           (dom/label nil displayname)
           (dom/span #js {:className "focus-border"})))

(defn option-input [component callback {:keys [db/id question/displayname question/value question/options]}]
  (dom/div nil
           (dom/label nil displayname)
           (dom/ul nil
                   (map (fn [{:keys [opt-label opt-value]}]
                          (dom/li #js {:key (str id opt-value)
                                       :onClick #(callback opt-value)}
                                  opt-label)) options))))

(defsc SurveyQuestion [this
                       {:keys [db/id question/displayname question/value
                               question/logicalname question/type question/options] :as props}
                       {:keys [value-change] :as computed}]
  {:query              [:db/id :question/displayname :question/type
                        :question/options :question/value :question/logicalname]
   :ident              [:survey-question/by-id :db/id]}
  (dom/li nil
          (case type
            :text (string-input this value-change props)
            :option (option-input this value-change props))))

(def ui-survey-question (prim/factory SurveyQuestion {:keyfn :db/id}))

(defsc Survey [this {:keys [survey/questions survey/title survey/entity db/id]}]
  {:ident         [:survey/by-id :db/id]
   :query         [:db/id {:survey/questions (prim/get-query SurveyQuestion)} :survey/entity :survey/title]}
  (let [question-props (fn [x]
                         (prim/computed x
                                        {:value-change
                                         (fn [v]
                                           (prim/transact! this
                                                           `[(api/set-question-value
                                                              {:id ~(:db/id x) :value ~v})]))}))]
    (dom/div nil (dom/h2 nil (str "Survey: " title))
             (dom/button #js {:onClick #(prim/transact! this
                                                        `[(api/submit-questions
                                                           ~{:entity entity :questions questions})])}
                         "Submit")
             (dom/ul #js {:className "survey"}
                     (map #(ui-survey-question (question-props %))
                          (sort-by :question/order questions))))))

(def ui-survey (prim/factory Survey {:keyfn :db/id}))

(defsc SurveyTile [this {:keys [db/id survey/title]}
                   {:keys [select-survey] :as computed}]
  {:ident         [:survey/by-id :db/id]

   :query [:db/id :survey/title]}
  (dom/div #js {:onClick #(select-survey id)} title))

(def ui-survey-tile (prim/factory SurveyTile {:keyfn :db/id}))

(defsc SurveyList [this {:keys [db/id survey-list/surveys ui/selected-survey]}]
  {:ident [:survey-list/by-id :db/id]
   :query [:db/id :ui/selected-survey {:survey-list/surveys (prim/get-query Survey)}]}
  (let [tile-props (fn [x]
                     (prim/computed x {:select-survey
                                       #(mut/set-value! this :ui/selected-survey %)}))]
    (dom/div nil ""
             (if selected-survey
               (ui-survey (first (filter #(= (:db/id %) ) surveys)))
               (dom/div nil
                        (dom/h2 nil "Select a survey to begin.")
                        (map #(ui-survey-tile (tile-props %)) surveys))))))

(def ui-survey-list (prim/factory SurveyList))

(defsc Application [this {:keys [ui/react-key ui/route ui/locale surveys] :or {ui/react-key "ROOT"}}]
  {:ident         (fn [] [:application :root])
   :initial-state {:ui/route :welcome}
   :query         [:ui/react-key :ui/route [:ui/locale '_] {:surveys (prim/get-query SurveyList)}]}
  (dom/div #js {:key (or react-key "ROOT")}
           (let [welcome (dom/div #js {:className "welcome"}
                                  (dom/h1 nil "StudyGate")
                                  (dom/h2 nil "A really catch tagline goes here.")
                                  ;; (dom/span nil "Getting things ready for you..")
                                  (if (= 0 (count surveys))
                                    (dom/span nil "Getting things ready for you..")
                                    (dom/button #js {:onClick
                                                     #(mut/set-value! this :ui/route :survey-list)}
                                                "Let's Go!")))
                 finished (dom/div nil
                                   (dom/h2 nil "All done!")
                                   (dom/h2 nil "Thanks for helping change the world.")
                                   (dom/button #js {:onClick #(prim/transact! this `[(api/reset)])}
                                               "Back to Surveys"))]
             (case route
               :survey-list (ui-survey-list surveys)
               :survey-finished finished
               welcome))))

(def ui-application (prim/factory Application))

(defsc Root [this {:keys [root/application :ui/react-key]}]
  {:initial-state (fn [p] {:ui/locale        "en-US"
                           :root/application (prim/get-initial-state Application {})})
   :query         [:ui/react-key {:root/application (prim/get-query Application)}]}
  (dom/div #js {:key react-key}
           (ui-application application)))

