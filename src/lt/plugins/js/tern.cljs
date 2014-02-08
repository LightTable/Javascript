(ns lt.plugins.js.tern
  (:require [lt.object :as object]
            [lt.objs.plugins :as plugins]
            [lt.objs.eval :as eval]
            [lt.objs.editor :as ed]
            [lt.objs.thread :as thread]
            [lt.plugins.auto-complete :as auto-complete]
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

;;(def ternserver-path (escape-spaces (files/join plugins/*plugin-dir* "node" "ternserver.js")))
(def ternserver-path (files/join plugins/user-plugins-dir "Javascript"  "node" "ternserver.js"))


(defn all-js-files [ws]
  (let [reg #"\.js$"
        func #(re-find reg %)
        ds (:folders @ws)
        fs (filter func (:files @ws))]
    (concat fs (mapcat #(files/filter-walk func %) ds))))

(defn ed->req [editor type]
  (let [path (-> @editor :info :path)]
    (clj->js {:query {:type (name type)
                      :file path
                      :end (ed/->cursor editor)}
              :files [{:name path
                      :text (ed/->val editor)
                      :type "full"}]})))


;; Client

(behavior ::send
          :triggers #{:send!}
          :reaction (fn [this msg]
                      (let [op (:command msg)
                            id (:cb msg)
                            info (:data msg)]
                        (.send (::worker @this)
                               (clj->js (merge {:id id :op op} info))))))


(behavior ::connect
          :triggers #{:connect}
          :reaction (fn [this]
                      (let [worker (::worker @this)
                            err (fn [e]
                                  (object/raise this :error! e))
                            ext (fn [code signal]
                                  (object/raise this :disconnect))
                            dis (fn []
                                  (object/raise this :disconnect))
                            msg (fn [m]
                                  (object/raise this :message [(symbol (.-id m))
                                                               (.-op m)
                                                               (.-data m)]))]
                        (.on worker "message" msg)
                        (.on worker "disconnect" dis)
                        (.on worker "exit" ext)
                        (.on worker "error" err))))


(behavior ::error
          :triggers #{:error}
          :reaction (fn [this e]
                      (.log js/console "Tern client error:")
                      (.log js/console e)))

(behavior ::kill
          :triggers #{:kill}
          :reactions (fn [this]
                       (clients/rem! this) ;; triggers :disconnect
                       (.kill (::worker @this))))

(behavior ::disconnect
          :triggers #{:disconnect}
          :reactions (fn [this]
                       (.disconnect (::worker @this))
                       (object/merge! this {:connected false})))

(behavior ::init
          :triggers #{:queue!}
          :reaction (fn [this _]
                      (when-not (:connected @this)
                        (let [cp (js/require "child_process")
                              worker (.fork cp ternserver-path #js ["--harmony"] #js {:execPath (files/lt-home (thread/node-exe)) :silent true})]
                          (object/merge! this {::worker worker})
                          (object/raise this :import-current-workspace)
                          (object/raise this :connect)))))

(behavior ::import-current-workspace ;; probably not what we really want to do
          :triggers #{:import-current-workspace}
          :reaction (fn [this]
                      (let [paths (all-js-files lt.objs.workspace/current-ws)]
                        (object/raise this :add-files paths))))

(behavior ::add-files ;; need to convert over add-files function
          :triggers #{:add-files}
          :reaction (fn [this paths]
                      (object/raise this :send {:type "addFiles"
                                                :msg paths})))

(object/object* ::tern.client
                :tags #{:client :tern.client}
                :init (fn [this] nil))

(def tern-client (object/create ::tern.client))


(clients/send tern-client :asdf
               {:type "request"
                :msg {:query {:type "completions"
                              :file "blergs.js"
                              :end {:ch 2 :line 1}}
                      :files [{:type "full"
                               :text "\r\nco\r\n"
                               :name "blergs.js"}]}}
              :only (fn [cmd data]
                      (.log js/console data)))
;; Autocomplete

(behavior ::trigger-update-hints
          :triggers #{:editor.javascript.hints.update!}
          :reaction (fn [editor]
                      (let [req (ed->req editor :completions)
                            cb (fn [data]
                                 (object/raise editor :editor.javascript.hints.result data))]
                        (clients/send ternserver-path req cb))))

(behavior ::finish-update-hints
          :triggers #{:editor.javascript.hints.result}
          :reaction (fn [editor res]
                      (->> (get-in (js->clj res) [:data :completions])
                           (map #(do #js {:completion %}))
                           (hash-map ::hints)
                           (object/merge! editor))
                      (object/raise auto-complete/hinter :refresh!)))

(behavior ::use-tern-hints
          :triggers #{:hints+}
          :reaction (fn [editor hints token]
                      (object/raise editor :editor.javascript.hints.update!)
                      (if-let [js-hints (::hints @editor)]
                        (concat hints js-hints)
                        hints)))

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
  (def cp (js/require "child_process"))
  (def worker (.fork cp ternserver-path #js ["--harmony"] #js {:execPath (files/lt-home (thread/node-exe)) :silent true}))

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

