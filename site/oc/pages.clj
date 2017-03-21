(ns oc.pages
  (:require [oc.terms :as terms]
            [oc.privacy :as privacy]))

(defn terms [options]
  (terms/terms options))

(defn privacy [options]
  (privacy/privacy options))

(defn index [options]
  [:div
   [:div.container.outer.sector
    [:div.container.inner
     [:div.sector-box
      [:div.sector-text.team-text
       [:h2 "Make Employees Great"]
       [:p
        "Everyone is on the same page with open access to company information."]
       [:h2 "Easy to Update"]
       [:p
        "Our "
        [:i.fa.fa-slack]
        " Slackbot works 24/7 with you and your team to keep everyone and everything up to date."]]
      [:div.team-image]]]]
   [:hr]
   [:div.container.outer.sector
    [:div.container.inner
     [:div.sector-box.even
      [:div.sector-text.update-text
       [:h2 "Impress Investors and Advisors"]
       [:p
        "Send regular investor updates that follow best practices and increase engagement."]
       [:h2 "Updates are Automatic"]
       [:p
        "Stakeholder updates are generated from your company's content for your approval."]]
      [:div.update-image]]]]
   [:hr]
   [:div.container.outer.sector
    [:div.container.inner
     [:div.sector-box
      [:div.sector-text.public-text
       [:h2 "Go Public!"]
       [:p
        "Share publicly to build trust and attract new investors, employees, and customers."]
       [:h2 "Play to the Crowd"]
       [:p
        "Keep crowd supporters and investors informed of your progress."]]
      [:div.public-image]]]]
   [:hr]
   [:div.container.outer.sector
    [:div.row.features
     [:div.col-md-4
      [:div.col-sm-2.feature-icon
       [:img.feature-icon {:src "/img/archive@4x.png"}]]
      [:div.col-sm-10.col-xs-12
       [:h2 "All in One Place"]
       [:p
        "All your company information; organized and easy to find."]]]
     [:div.col-md-4
      [:div.col-sm-2.feature-icon
       [:img.feature-icon {:src "/img/best-practices@4x.png"}]]
      [:div.col-sm-10.col-xs-12
       [:h2 "Best Practices"]
       [:p
        "Core topics and guidelines help you decide what to share."]]]
     [:div.col-md-4
      [:div.col-sm-2.feature-icon
       [:img.feature-icon {:src "/img/concierge@4x.png"}]]
      [:div.col-sm-10.col-xs-12
       [:h2 "Concierge Service"]
       [:p "Support to create beautiful, concise and meaningful updates."]]]]]])

(defn pricing [options]
   [:div.container.outer.sector.content
    [:div.row
     [:div.col-md-12.pricing-header
      [:h2 "Simple Pricing"]
      [:p "Transparent prices for any need."]]]
    [:div.row
     "<!-- Pricing Item -->"
     [:div.col-md-4
      [:div.pricing.hover-effect
       [:div.pricing-name
        [:h3 "Team"
         [:span "Internal Slack distrbution"]]]
       [:div.pricing-price [:h4 [:i "$"] "25" [:span "Per Month"]]]
       [:ul.pricing-content.list-unstyled
        [:li "Stakeholder dashboard"]
        [:li "Rich Slack integration"]]
       [:div.pricing-footer
        [:p "Perfect for keeping your team members informed."]
        [:p "Optionally involve the crowd with a public stakeholder dashboard."]]]]
     [:div.col-md-4
      [:div.pricing.hover-effect
       [:div.pricing-name
        [:h3 "Stakeholders"
         [:span "Periodic stakeholder updates"]]]
       [:div.pricing-price [:h4 [:i "$"] "50" [:span "Per Month"]]]
       [:ul.pricing-content.list-unstyled
        [:li "Stakeholder dashboard"]
        [:li "Rich Slack integration"]
        [:li [:b "Periodic stakeholder updates"]]]
       [:div.pricing-footer
        [:p "Keep your busy investors and advisors informed with periodic updates that follow best practices."]]]]
     [:div.col-md-4
      [:div.pricing.hover-effect
       [:div.pricing-name
        [:h3 "Concierge"
         [:span "Beautifully designed content"]]]
       [:div.pricing-price [:h4 [:i "$"] "250" [:span "Per Month"]]]
       [:ul.pricing-content.list-unstyled
        [:li "Stakeholder dashboard"]
        [:li "Rich Slack integration"]
        [:li "Periodic stakeholder updates"]
        [:li [:b "Concierge support to desgin custom stakeholder content *"]]]
       [:div.pricing-footer
        [:p "Impress your investors and advisors with beautful, concise and meaningful updates."]]]]
     ;; "<!--         <div class=\"col-md-4 sm-12\">\n          <h2>Team</h2>\n          <p>Internal distribution with Slack.</p>\n        </div>\n        <div class=\"col-md-4 sm-12\">\n          <h2>Stakeholders</h2>\n          <p>Periodic stakeholder updates distributed automatically.</p>\n        </div>\n        <div class=\"col-md-4 sm-12\">\n          <h2>Concierge</h2>\n          <p>Beautiful stakeholder updates, hand-crafted by content creation professionals.</p>\n        </div>\n -->"
     ]])

(defn about [options]
  [:div.container.outer.sector.content.about
   [:div.container.inner
    [:div.row
     [:div.col-md-12
      [:h2 "Transparency Simplified."]
      [:p "We help founders turn startup transparency into a competitive advantage."]
      [:p "OpenCompany makes it easy to keep employees, investors, advisors, and customers updated and on the same page. We provide a guide based on best practices and automate distribution of the information to save you time."]
      [:p "As an Open Source Software project, you can follow our progress or contribute code on "
       [:a {:href "https://github.com/open-company"} "GitHub"] "."]
      [:p "We can't build this open platform in a vacuum. Our goal is to work with everyone in the startup community: founders, employees and investors. Together we can define how the next generation of great startups will be created. We hope you'll join our community and contribute your experience and opinions."]
      [:p "We’re also looking for awesome people that are interested in startup transparency. We are a fully distributed team working from our home offices around the world. Join us."]]]]])

(defn not-found [{contact-mail-to :contact-mail-to contact-email :contact-email}]
  [:div.container.outer.sector.not-found
   [:div.container.inner
    [:div.row
     [:div.col-md-12
      [:div.error-page
       [:h1 "404"]
       [:h2 "Hmm, this does not look right."]
       [:p
        "You seem to have come across a page that does not yet exist."
        [:br]
        "Please try again or contact support: "
        [:a {:href contact-mail-to} contact-email]]
       [:a.btn {:href "/"} "Return To Home"]
       [:script {:src "/js/set-path.js"}]]]]]])

(defn server-error [{contact-mail-to :contact-mail-to contact-email :contact-email}]
  [:div.container.outer.sector.server-error
   [:div.container.inner
    [:div.row
     [:div.col-md-12
      [:div.error-page
       [:h1 "500"]
       [:h2 "Hmm, this does not look right."]
       [:p
        "You seem to have come across an error."
        [:br]
        "Please try again or contact support: "
        [:a {:href contact-mail-to} contact-email]]
       [:a.btn {:href "/"} "Return To Home"]
       [:script {:src "/js/set-path.js"}]]]]]])

(def app-shell
  {:head [:head
          [:meta {:charset "utf-8"}]
          [:meta {:content "IE=edge", :http-equiv "X-UA-Compatible"}]
          [:meta {:content "width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=1.0, user-scalable=no", :name "viewport"}]
          [:meta {:name "apple-mobile-web-app-capable" :content "yes"}]
          ;; The above 3 meta tags *must* come first in the head;
          ;; any other head content must come *after* these tags
          [:title "OpenCompany - Startup Transparency Made Simple"]
          ;; Reset IE
          "<!--[if lt IE 9]><script src=\"//html5shim.googlecode.com/svn/trunk/html5.js\"></script><![endif]-->"
          ;; Bootstrap CSS //getbootstrap.com/
          [:link {:rel "stylesheet" :href "//maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css" :integrity "sha384-BVYiiSIFeK1dGmJRAkycuHAHRg32OmUcww7on3RYdg4Va+PmSTsz/K68vbdEjh4u" :crossorigin "anonymous"}]
          ;; Normalize.css //necolas.github.io/normalize.css/
          ;; TODO inline this into app.main.css
          [:link {:rel "stylesheet" :href "/css/normalize.css?oc_deploy_key"}]
          ;; Font Awesome icon fonts //fortawesome.github.io/Font-Awesome/cheatsheet/
          [:link {:rel "stylesheet" :href "//maxcdn.bootstrapcdn.com/font-awesome/4.5.0/css/font-awesome.min.css"}]
          ;; OpenCompany CSS
          [:link {:type "text/css" :rel "stylesheet" :href "/css/app.main.css?oc_deploy_key"}]
          ;; jQuery UI CSS
          [:link {:rel "stylesheet" :href "//ajax.googleapis.com/ajax/libs/jqueryui/1.12.1/themes/smoothness/jquery-ui.css"}]
          ;; Emoji One Autocomplete CSS
          [:link {:type "text/css" :rel "stylesheet" :href "/css/emojione/autocomplete.css?oc_deploy_key"}]
          ;; Google fonts Domine and OpenSans
          [:link {:type "text/css" :rel "stylesheet" :href "https://fonts.googleapis.com/css?family=Open+Sans:400,700,600,300,800|Domine:400,700"}]
          ;;  Medium Editor css
          [:link {:type "text/css" :rel "stylesheet" :href "/css/medium-editor/medium-editor.css?oc_deploy_key"}]
          [:link {:type "text/css" :rel "stylesheet" :href "/css/medium-editor/default.css?oc_deploy_key"}]
          ;; Emojione Sprites CSS
          [:link {:type "text/css" :rel "stylesheet" :href "/css/emojione.css?oc_deploy_key"}]
          ;; EmojionePicker css from cljsjs
          [:link {:type "text/css" :rel "stylesheet" :href "/css/emojione-picker.css?oc_deploy_key"}]
          ;; Filestack
          [:script {:type "text/javascript" :src "//static.filestackapi.com/v3/filestack-0.1.10.js"}]
          [:script {:type "text/javascript" :src "/lib/print_ascii.js"}]]
   :body [:body
          [:div#app [:div.oc-loading.active [:i.fa.fa-circle-o-notch.fa-spin]]]
          [:div#oc-loading]
          ;; Custom Tooltips
          [:script {:type "text/javascript" :src "/lib/tooltip/tooltip.js"}]
          ;; jQuery needed by Bootstrap JavaScript
          [:script {:src "//ajax.googleapis.com/ajax/libs/jquery/2.1.4/jquery.min.js" :type "text/javascript"}]
          ;; Truncate html string
          [:script {:type "text/javascript" :src "/lib/truncate/jquery.truncate.js"}]
          ;; jQuery textcomplete needed by Emoji One autocomplete
          [:script {:src "//cdnjs.cloudflare.com/ajax/libs/jquery.textcomplete/1.7.3/jquery.textcomplete.min.js" :type "text/javascript"}]
          ;; WURFL used for mobile/tablet detection
          [:script {:type "text/javascript" :src "//wurfl.io/wurfl.js"}]
          ;; jQuery scrollTo plugin
          [:script {:src "/lib/scrollTo/scrollTo.min.js?oc_deploy_key" :type "text/javascript"}]
          ;; jQuery UI
          [:script {:src "//ajax.googleapis.com/ajax/libs/jqueryui/1.12.1/jquery-ui.min.js" :type "text/javascript"}]
          ;; Resolve jQuery UI and Bootstrap tooltip conflict
          [:script "$.widget.bridge('uitooltip', $.ui.tooltip);"]
          ;; Bootstrap JavaScript //getf.com/
          [:script {:src "//maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js" :type "text/javascript" :integrity "sha384-Tc5IQib027qvyjSMfHjOMaLkfuWVxZxUPnCJA7l2mCWNIpG9mGCD8wGNIcPD7Txa" :crossorigin "anonymous"}]
          ;; Emoji One Autocomplete
          [:script {:src "/js/emojione/autocomplete.js?oc_deploy_key" :type "text/javascript"}]
          ;; JWT Decode lib
          [:script {:src "/lib/jwt-decode/jwt-decode.min.js?oc_deploy_key" :type "text/javascript"}]
          ;; ClojureScript generated JavaScript
          [:script {:src "/js/oc.js?oc_deploy_key" :type "text/javascript"}]
          ;; Utilities
          [:script {:type "text/javascript", :src "/lib/js-utils/svg-utils.js?oc_deploy_key"}]
          [:script {:type "text/javascript", :src "/lib/js-utils/pasteHtmlAtCaret.js?oc_deploy_key"}]
          ;; Clean HTML input
          [:script {:src "/lib/cleanHTML/cleanHTML.js?oc_deploy_key" :type "text/javascript"}]
          ;; MediumEditorAutolist
          [:script {:type "text/javascript" :src "/lib/MediumEditorAutolist/autolist.js"}]]})