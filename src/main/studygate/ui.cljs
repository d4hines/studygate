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

;; Corresponds to a short-text field in CRM.
(defmethod render-question :text [this {:keys [db/id question/displayname question/value question/options]}]
  (.log js/console (count value))
  (dom/div #js {:className "question"}
           (dom/input #js {:className (if (not= 0 (count value)) "has-content")
                           :type      "text"
                           :value     (or value "")
                           :onChange  #(let [val (-> % .-target .-value)]
                                         (set-value this id val))})
           (dom/label nil displayname)
           (dom/span #js {:className "focus-border"})))

;; Corresponds to either a boolean or option-set field in CRM (renders both the same).
(defmethod render-question :option [this {:keys [db/id question/displayname question/value question/options]}]
  (dom/div #js {:className "question"}
           (dom/label nil displayname)
           (dom/ul nil
                   (map (fn [{:keys [opt-label opt-value]}]
                          (let [classname (if-not (nil? value)
                                            (if (= opt-value value) "selected" "not-selected")
                                            nil)]
                            (dom/li #js {:className "option-label"
                                         :key     (str id opt-value)
                                         :onClick #(set-value this id opt-value)}
                                    (dom/span #js {:className classname} opt-label)))) options))))

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
  [this {:keys [survey/questions survey/title survey/entity db/id survey/image]}]
  {:ident         [:survey/by-id :db/id]
   :query         [:db/id {:survey/questions (prim/get-query SurveyQuestion)}
                   :survey/image :survey/entity :survey/title]}
  (let [question-props (fn [x]
                         (prim/computed x
                                        {:value-change
                                         (fn [v]
                                           (prim/transact! this
                                                           `[(api/set-question-value
                                                              {:id ~(:db/id x) :value ~v})]))}))]
    (dom/div #js {:className "survey-container"}
             (dom/h2 #js {:id "survey-title"} (str "Survey: " title))
             (dom/ul #js {:className "survey"}
                     (map #(ui-survey-question (question-props %))
                          (sort-by :question/order questions)))
             (dom/a #js {:className "button"
                         :id "submit"
                         :onClick #(prim/transact! this
                                                        `[(api/submit-questions
                                                           ~{:entity entity :questions questions})])}
                         "Submit!"))))

(def ui-survey (prim/factory Survey {:keyfn :db/id}))

;; Renders a tile by which the user can select a survey.
;; The image url is taken straight from the CRM entity description
(defsc SurveyTile [this {:keys [db/id survey/title survey/image]}
                   {:keys [select-survey] :as computed}]
  {:ident         [:survey/by-id :db/id]
   :query [:db/id :survey/title :survey/image]}
  (dom/img #js {:onClick #(routing/nav! (str "surveys/" id))
                :src image}))

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
    (if selected-survey
      (ui-survey (first (filter #(= (:db/id %) selected-survey) surveys)))
      (dom/div #js {:className "surveys"}
               (dom/h2 nil "Select a survey to begin.")
               (dom/div #js {:className "img-container"}
                        (map #(ui-survey-tile (tile-props %)) surveys))))))

(def ui-survey-list (prim/factory SurveyList))

;; The UI for the welcome screen.
;; Doesn't include the floating cloud or logo. See index.html.
(defn welcome [surveys]
  (dom/div #js {:className "welcome"}
           (dom/h1 nil "StudyGate")
           (dom/h2 nil "A really catch tagline goes here.")
           ;; (dom/span nil "Getting things ready for you..")

           (if (= 0 (count surveys))
             (dom/span #js {:className "loading-data"}
                       "Getting things ready for you...")
             (dom/a #js {:onClick #(routing/nav! "surveys")
                         :className "button"}
                    "Let's Go!"))
           (dom/div #js {:className "logo"}
                    (dom/span nil "powered by")
                    (dom/img #js {:src "./images/logo.png"})
                    (dom/span nil "or at least, it could be..."))))

(defn reset [this]
  (do
    (routing/nav! "surveys")
    (prim/transact! this `[(api/reset)])))

;; UI for the "Finished" screen.
;; TODO This should be it's own routed component.
(defn finished [this]
  (dom/div #js {:className "done"}
           (dom/h2 nil "All done!")
           (dom/h3 nil "Thanks for helping change the world.")
           (dom/a #js {:className "button"
                       :onClick #(reset this)}
                       "Back to Surveys")))

;; Uses DOM switching to render either the welcome page, the survey list, or the "finished" page
;; TODO Switch to Fulcro UI Routing instead.
;; OPTIMIZE This component adds an extra and perhaps unnecessary level away from Root, and can
;; probably be refactored out.
(defsc Application [this {:keys [ui/react-key ui/route ui/locale surveys ui/show-modal] :or {ui/react-key "ROOT"}}]
  {:ident         (fn [] [:application :root])
   :initial-state {:ui/route :welcome :ui/show-modal false}
   :query         [:ui/react-key :ui/route [:ui/locale '_]
                   :ui/show-modal {:surveys (prim/get-query SurveyList)}]}
  (dom/div #js {:key (or react-key "ROOT")}
           (if (not= route :welcome)
             (dom/div {:className "navbar"}
                      (dom/div #js {:className "help"
                                    :onClick #(mut/toggle! this :ui/show-modal)}
                               (dom/span nil "?"))
                      (dom/h1 #js {:className "logo"} "StudyGate")))
           (if show-modal
             (dom/div #js {:className "overlay"}
                      (dom/div {:className "popup"}
                               (dom/div #js {:className "popup-header"}
                                        (dom/h3 nil "Made with love by Daniel Hines")
                                        (dom/span {:onClick #(mut/toggle! this :ui/show-modal)
                                                   :className "close"} "x"))
                               (dom/p nil (str "If you work at Reify Health and are enjoying"
                                               " StudyGate, you should consider giving me an interview."
                                               " After all, I made this just for you!"))))
             (case route
               :survey-list (ui-survey-list surveys)
               :survey-finished (finished this)
               (welcome surveys)))))

(def ui-application (prim/factory Application))

(defsc Root [this {:keys [root/application :ui/react-key]}]
  {:initial-state (fn [p] {:ui/locale        "en-US"
                           :root/application (prim/get-initial-state Application {})})
   :query         [:ui/react-key {:root/application (prim/get-query Application)}]}
  (ui-application application))

