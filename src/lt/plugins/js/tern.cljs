(ns lt.plugins.js.tern
  (:require [lt.object :as object]
            [lt.objs.plugins :as plugins]
            [lt.objs.editor :as ed]
            [lt.objs.thread :as thread]
            [lt.plugins.auto-complete :as auto-complete]
            [lt.objs.clients.ws :as ws]
            [lt.objs.files :as files]
            [lt.objs.clients :as clients]
            [lt.objs.editor.pool :as pool]
            [lt.objs.notifos :as notifos]
            [lt.objs.sidebar.command :as cmd]
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

;;****************************************************
;; Client
;;****************************************************

(behavior ::send
          :triggers #{:send!}
          :reaction (fn [this msg]
                      (.send (::worker @this)
                             (clj->js msg))))


(behavior ::connect
          :triggers #{:connect}
          :reaction (fn [this]
                      (let [worker (::worker @this)
                            dis (fn [code signal]
                                  (object/raise this :disconnect))
                            msg (fn [m]
                                  (object/raise this :message [(symbol (.-cb m))
                                                               (.-command m)
                                                               (.-data m)]))]
                        (.on worker "message" msg)
                        (.on worker "disconnect" dis)
                        (.on worker "exit" dis))))


(behavior ::kill
          :triggers #{:kill}
          :reaction (fn [this]
                      (object/raise this :disconnect)
                      (when-let [worker (::worker @this)]
                        (.kill worker)
                        (object/merge! this {::worker nil}))))


(behavior ::disconnect
          :triggers #{:disconnect}
          :reaction (fn [this]
                      (when-let [worker (::worker @this)]
                        (when (.-connected worker)
                          (.disconnect worker)))
                      (object/merge! this {:connected false})
                      (notifos/set-msg! (str "Disconnected from Javascript auto-complete server"))))


(behavior ::init
          :triggers #{:try-send!}
          :order -7
          :reaction (fn [this _]
                      (when-not (:connected @this)
                        (let [cp (js/require "child_process")
                              worker (.fork cp ternserver-path #js ["--harmony"] #js {:execPath (files/lt-home (thread/node-exe)) :silent true})]
                          (object/merge! this {::worker worker})
                          (object/raise this :connect)
                          (notifos/set-msg! (str "Connected to Javascript auto-complete server"))
                          (object/raise this :import-current-workspace)))))


(behavior ::import-current-workspace ;; probably not what we really want to do
          :triggers #{:import-current-workspace}
          :reaction (fn [this]
                      (let [paths (all-js-files lt.objs.workspace/current-ws)]
                        (object/raise this :add-files paths))))


(behavior ::add-files ;; need to convert over add-files function
          :triggers #{:add-files}
          :reaction (fn [this paths]
                      (clients/send this :addfiles paths)))


(object/object* ::tern.client
                :tags #{:client :tern.client}
                :name "Tern Javascript Server"
                :init (fn [this] nil))

(def tern-client (object/create ::tern.client))

;;****************************************************
;; Autocomplete
;;****************************************************

(behavior ::trigger-update-hints
          :triggers #{:editor.javascript.hints.update!}
          :reaction (fn [editor]
                      (let [req (ed->req editor :completions)
                            cb (fn [_ data]
                                 (object/raise editor :editor.javascript.hints.result data))]
                        (clients/send tern-client :request req :only cb))))


(behavior ::finish-update-hints
          :triggers #{:editor.javascript.hints.result}
          :reaction (fn [editor res]
                      (->> res
                           (.-completions)
                           (map #(do #js {:completion %}))
                           (hash-map ::hints)
                           (object/merge! editor))
                      (object/raise auto-complete/hinter :refresh!)))


(behavior ::use-tern-hints
          :triggers #{:hints+}
          :reaction (fn [editor hints token]
                      (when (not= token (::token @editor))
                        (object/merge! editor {::token token})
                        (object/raise editor :editor.javascript.hints.update!))
                      (if-let [js-hints (::hints @editor)]
                        js-hints
                        hints)))


(behavior ::clear-token
          :triggers #{:select :select-unknown :escape!}
          :reaction (fn []
                      (cmd/exec! ::clear-token)))


(cmd/command {:command ::clear-token
              :desc "Editor: Clear last Tern token"
              :exec (fn []
                      (object/merge! (pool/last-active) {::token :none
                                                         ::hints nil}))})


(cmd/command {:command :tern.kill
              :desc "Tern: Kill the Tern auto-completion server"
              :exec (fn []
                      (object/raise tern-client :kill))})
