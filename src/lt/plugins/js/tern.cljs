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
            [lt.objs.editor.pool :as pool]
            [lt.objs.notifos :as notifos]
            [lt.objs.sidebar.command :as cmd]
            [lt.objs.popup :as popup]
            [lt.plugins.watches :as watches]
            [lt.util.load :as load]
            [clojure.string :as string]
            [lt.util.dom :refer [$ append]]
            [lt.util.cljs :refer [js->clj]])
  (:require-macros [lt.macros :refer [behavior defui]]))

(def tern-module-dir
  (let [a (files/join plugins/user-plugins-dir "Javascript"  "node_modules" "tern")
        b (files/join plugins/plugins-dir "Javascript" "node_modules" "tern")
        c (files/join plugins/plugins-dir "javascript" "node_modules" "tern")
        e (files/join plugins/*plugin-dir* "node_modules" "tern")]
    (cond
     (files/dir? a) a
     (files/dir? b) b
     (files/dir? c) c
     :else e)))

(defn tern-def [name]
  (-> (files/join tern-module-dir "defs" name)
      (files/bomless-read)
      (js/JSON.parse)))

(defn tern-server [opts]
  (let [TernServer (.-Server (js/require tern-module-dir))]
    (TernServer. (clj->js opts))))

(defn add-files [ts paths]
  (let [server (::instance @ts)]
    (doseq [p paths]
      (.addFile server p (files/bomless-read p)))))

(defn all-js-files [ws]
  (let [reg #"\.js$"
        func #(re-find reg %)
        ds (:folders @ws)
        fs (filter func (:files @ws))]
    (concat fs (mapcat #(files/filter-walk func %) ds))))

(comment
  ;; Position
  (-> (pool/last-active)
      (ed/->cursor))

  ;; Text value
  (-> (pool/last-active)
      (ed/->val))

  ;; Path
  (-> (pool/last-active)
      deref
      :info
      :path))


(behavior ::start-server
          :triggers #{:object.instant}
          :order -7
          :reaction (fn [this]
                      (let [server (tern-server {:getFile (fn [path]
                                                            (files/bomless-read path))
                                                 :async false
                                                 :def [(tern-def "browser.json")
                                                       (tern-def "ecma5.json")]})]
                        (object/merge! this {::instance server}))
                      (object/raise this :import-current-workspace)))

(behavior ::import-current-workspace
          :triggers #{:import-current-workspace}
          :reaction (fn [this]
                      (let [paths (all-js-files lt.objs.workspace/current-ws)]
                        (object/raise this :add-files paths))))

(behavior ::add-files
          :triggers #{:add-files}
          :reaction (fn [this paths]
                      (add-files this paths)))

(object/object* ::tern-server
                :tags #{:tern-server}
                :behaviors [::import-current-workspace
                            ::add-files]
                :init (fn [this] nil))

(def ts (object/create ::tern-server))


;; Playground
(comment
  (behavior ::js-hints
            :triggers #{:hints+}
            :reaction (fn [editor hints token]
                        (do
                          (identity token)
                          (concat hints [#js {:completion "blergs123"}]))))

  (def fs (js/require "fs"))

  (def js-re #"\.js$")

  (->> @lt.objs.workspace/current-ws
       :folders
       (mapcat #(files/filter-walk (fn [x]
                                     (re-find js-re x)) %)))

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

  @compl)

