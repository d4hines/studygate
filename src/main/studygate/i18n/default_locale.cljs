(ns studygate.i18n.default-locale (:require studygate.i18n.en-US [fulcro.i18n :as i18n]))

(reset! i18n/*current-locale* "en-US")

(swap! i18n/*loaded-translations* #(assoc % :en-US studygate.i18n.en-US/translations))