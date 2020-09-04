(ns oc.pages.index
  (:require [oc.pages.shared :as shared]))

(defn index [options]
  [:div.home-wrap.group
    {:id "wrap"}
    [:div.main.home-page
      ; Hope page header
      [:section.cta.group

        [:h1.headline
          "Your team’s news feed"]
        [:div.subheadline.big-web-tablet-only
          "The latest updates from your team, simplified."]

        [:div.get-started-button-container.group
          [:button.mlb-reset.get-started-button.get-started-action
            {:id "get-started-centred-bt"}
            "Try Carrot"]
          [:span.get-started-subtitle
            "Open source and free."]]

        ; [:div.main-animation-container
        ;   [:img.main-animation
        ;     {:src (shared/cdn "/img/ML/homepage_screenshot.png")
        ;      :alt "Carrot"
        ;      :srcSet (str
        ;               (shared/cdn "/img/ML/homepage_screenshot@2x.png") " 2x, "
        ;               (shared/cdn "/img/ML/homepage_screenshot@3x.png") " 3x, "
        ;               (shared/cdn "/img/ML/homepage_screenshot@4x.png") " 4x")}]]

        shared/testimonials-logos-line]

      (shared/testimonials-section :index)

      [:section.video
        [:div.main-animation-container
          [:img.main-animation
            {:src (shared/cdn "/img/ML/homepage_screenshot.png")
             :alt "Carrot"
             :srcSet (str
                      (shared/cdn "/img/ML/homepage_screenshot@2x.png") " 2x, "
                      (shared/cdn "/img/ML/homepage_screenshot@3x.png") " 3x, "
                      (shared/cdn "/img/ML/homepage_screenshot@4x.png") " 4x")}]]]

      shared/pricing-footer
      ]])