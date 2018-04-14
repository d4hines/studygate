(ns studygate.ui
  (:require [fulcro.client.primitives :as prim :refer [defsc]]
            [fulcro.client.mutations :as mut :refer [defmutation]]
            [studygate.api :as api]
            [secretary.core :as secretary]
            [goog.events]
            [fulcro.client.alpha.dom :as dom]
            [fulcro.client :as fc]
            [studygate.routing :as routing]))

(defmulti render-question
  "New data types can be supported simply by extending this multimethod."
  (fn [question-component props] (:question/type props)))

(defn set-value [this id val]
  (prim/transact! this `[(api/set-question-value ~{:id id :value val})]))

;; Corresponds to a short-text field in CRM
(defmethod render-question :text [this {:keys [db/id question/displayname question/value question/options]}]
  (dom/div #js {:className "question"}
           (dom/input #js {:className "effect-17"
                           :type      "text"
                           :value     (or value "")
                           :onChange  #(let [val (-> % .-target .-value)]
                                         (set-value this id val))})
           (dom/label nil displayname)
           (dom/span #js {:className "focus-border"})))

;; Corresponds to either a boolean or option-set field in CRM (renders both the same).
(defmethod render-question :option [this {:keys [db/id question/displayname question/value question/options]}]
  (dom/div nil
           (dom/label nil displayname)
           (dom/ul nil
                   (map (fn [{:keys [opt-label opt-value]}]
                          (dom/li #js {:key     (str id opt-value)
                                       :onClick #(set-value this id opt-value)}
                                  opt-label)) options))))

(defsc SurveyQuestion [this props]
  {:query [:db/id :question/displayname :question/type
           :question/options :question/value :question/logicalname]
   :ident [:survey-question/by-id :db/id]}
  (dom/li nil (render-question this props)))

(def ui-survey-question (prim/factory SurveyQuestion {:keyfn :db/id}))

;;; Survey Component
;; Renders a list of questions corresponding to a single entity in CRM.
(defsc Survey
  ""
  [this {:keys [survey/questions survey/title survey/entity db/id]}]
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

;; Renders a tile by which the user can select a survey.
;; The image url is taken straight from the CRM entity description
(defsc SurveyTile [this {:keys [db/id survey/title]}
                   {:keys [select-survey] :as computed}]
  {:ident         [:survey/by-id :db/id]

   :query [:db/id :survey/title]}
  (dom/div #js {:onClick #(routing/nav! (str "surveys/" id))} title))

(def ui-survey-tile (prim/factory SurveyTile {:keyfn :db/id}))

;; Renders either a list of survey tiles or a single survey.
;; Uses simple DOM switching to do so.
;; TODO Switch to Fulcro UI Routing instead.
(defsc SurveyList [this {:keys [db/id survey-list/surveys ui/selected-survey]}]
  {:ident [:survey-list/by-id :db/id]
   :query [:db/id [:ui/selected-survey '_] {:survey-list/surveys (prim/get-query Survey)}]}
  (let [tile-props (fn [x]
                     (prim/computed x {:select-survey
                                       #(prim/transact! this `[(api/select-survey {:id ~%})])}))]
    (dom/div nil ""
             (if selected-survey
               (ui-survey (first (filter #(= (:db/id %) selected-survey) surveys)))
               (dom/div nil
                        (dom/h2 nil "Select a survey to begin.")
                        (map #(ui-survey-tile (tile-props %)) surveys))))))

(def ui-survey-list (prim/factory SurveyList))

(defn reset [this]
  (routing/nav! "surveys")
  (prim/transact! this `[(api/reset)]))

;; Uses DOM switching to render either the welcome page, the survey list, or the "finished" page
;; TODO Switch to Fulcro UI Routing instead.
;; OPTIMIZE This component adds an extra and perhaps unnecessary level away from Root, and can
;; probably be refactored out.
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
                                    (dom/button #js {:onClick #(routing/nav! "surveys")}
                                                "Let's Go!")))
                 finished (dom/div nil
                                   (dom/h2 nil "All done!")
                                   (dom/h2 nil "Thanks for helping change the world.")
                                   (dom/button #js {:onClick #(reset this)}
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

