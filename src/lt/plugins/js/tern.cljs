(ns lt.plugins.js.tern
  (:require [lt.object :as object]
            [lt.objs.plugins :as plugins]
            [lt.objs.eval :as eval]
            [lt.objs.editor :as ed]
            [lt.objs.clients.ws :as ws]
            [lt.objs.files :as files]
            [lt.objs.clients :as clients]
            [lt.objs.sidebar.clients :as scl]
            [lt.objs.browser :as browser]
            [lt.objs.notifos :as notifos]
            [lt.objs.sidebar.command :as cmd]
            [lt.objs.popup :as popup]
            [lt.plugins.watches :as watches]
            [lt.util.load :as load]
            [clojure.string :as string]
            [lt.util.dom :refer [$ append]]
            [lt.util.cljs :refer [js->clj]])
  (:require-macros [lt.macros :refer [behavior defui]]))


(behavior ::js-hints
          :triggers #{:hints+}
          :reaction (fn [editor hints token]
                      (do
                        (identity token)
                        (concat hints [#js {:completion "blergs123"}]))))

(defn user-plugin-info [name]
  (let [d plugins/user-plugins-dir
        path (files/join d name)
        nm (files/join path "node_modules")]
    {:name name
     :path path
     :node-modules-dir nm}))

(defn user-module [name mod]
  (-> (user-plugin-info name)
      :node-modules-dir
      (files/join mod)
      (js/require)))

(defn tern-def [name]
  (let [info (user-plugin-info "Javascript")]
    (-> (files/join (:node-modules-dir info) "tern" "defs" name)
        (files/bomless-read)
        (js/JSON.parse))))






;; Playground

(def tern (user-module "Javascript" "tern" ))

(def server (tern.Server. #js {:getFile (fn [path]
                                          (files/bomless-read path))
                               :async false
                               :defs #js [(tern-def "browser.json")
                                          (tern-def "ecma5.json")]
                               :plugins #js {}}))

(def test-file (files/join (:path (user-plugin-info "Javascript" ))
                                              "tern-testing.js" ))

(def other-file (files/join (:path (user-plugin-info "Javascript" ))
                                              "blah.js" ))

(def compl (atom nil))

(.addFile server other-file)

(.request server (clj->js {:query {:type "completions"
                                   :file test-file
                                   :end {:ch 2
                                         :line 7}
                                   :lineCharPositions true
                                   :docs true
                                   :types true}
                           :file [{:name test-file
                                   :text (files/bomless-read test-file)}]})
          (fn [e data]
            (swap! compl (fn [_ d] d) data)))

@compl

