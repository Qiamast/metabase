(ns metabase.events.dependencies
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [metabase.config :as config]
            [metabase.events :as events]
            (metabase.models [card :refer [Card]]
                             [dependency :refer [IDependent] :as dependency])))


(def dependencies-topics
  "The `Set` of event topics which are subscribed to for use in dependencies tracking."
  #{:card-create
    :card-update})

(def ^:private dependencies-channel
  "Channel for receiving event notifications we want to subscribe to for dependencies events."
  (async/chan))


;;; ## ---------------------------------------- EVENT PROCESSING ----------------------------------------


(def model->entity
  {:card Card})

(defn process-dependencies-event
  "Handle processing for a single event notification received on the dependencies-channel"
  [dependency-event]
  ;; try/catch here to prevent individual topic processing exceptions from bubbling up.  better to handle them here.
  (try
    (when-let [{topic :topic object :item} dependency-event]
      (let [model   (events/topic->model topic)
            entity  (model->entity (keyword model))
            id      (events/object->model-id topic object)]
        ;; entity must support dependency tracking to continue
        (when (satisfies? IDependent entity)
          (let [deps (dependency/dependencies entity id object)]
            (dependency/update-dependencies entity id deps)))))
    (catch Throwable e
      (log/warn (format "Failed to process dependencies event. %s" (:topic dependency-event)) e))))



;;; ## ---------------------------------------- LIFECYLE ----------------------------------------


(defn events-init []
  (when-not (config/is-test?)
    (log/info "Starting dependencies events listener")
    (events/start-event-listener dependencies-topics dependencies-channel process-dependencies-event)))
