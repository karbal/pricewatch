(ns reaction.pricewatch.processor
  "A Kafka Streams app that processes The prices-by-id Kafka topic from the
  Reaction Pricing Engine and watches for price changes that match a set of
  pricewatch records.

  A very rough outline of the topology:

  - Consume price changes from the prices-by-id Kafka topic. This topic is
    produced by Reaction's Pricing Engine application. It contains a aggregate
    of all prices keyed by pricebook-entry-id.

  - For each SKU where price is changed:

    - Look for entries in the watches table that matches the sku
    - Evaluate pricewatch rules to determine if there are any active matches.
    - Take action when a match is found. This simple example This can be printing, producing to
      another topic, or taking direct action.

  The processor is a Lightweight component wrapper around jackdaw Streams (DSL)
  processor app."
  (:require
    [com.stuartsierra.component :refer [start stop]]
    [integrant.core :as ig]
    [jackdaw.streams :as streams]
    [rp.jackdaw.processor :as processor]))


(def watches-table
  "A simple in-memory data store for the price watches. The data is an index of
  product-id to watches. The in-memory version is a map. Keys are the product-id
  and values are vectors of watches for that key. A production app would provide
  management for this data set."
  (atom {"product1" [{:user-id "demo"
                      :email "code-examples@reactioncommerce.com"
                      :product-id "sku1"
                      :start-price 100.00}]}))


(defn watches-for-product [db product-id] (get @db product-id))

(defn lowest-price
  "Gets the lowest price available in the price-by-id record. This simple
  implmentation takes the minimum of all prices."
  [prices-by-id]
  80.00)

(defn pricewatch-match?
  "Predicate that returns true when pricewatch conditions are met. This
  implementation checks if the new price has dropped 20% from the price set at
  the start of the watch."
  [prices-by-id watch]
  (boolean
    (when (> (:start-price watch) 0)
      (let [change-threshold 0.8
            percent-change (/ (lowest-price prices-by-id) (:start-price watch))]
        (<= percent-change change-threshold)))))

(defn act-on-pricewatch-match!
  "Performs actions when a pricewatch has an active match, presumably with
  side-effects."
  [price-by-id watch]
  (println "Notification: Price dropped!" watch))

(defn do-watches!
  "Performs price watches given a product-id `k`, prices-by-id value `v` and a
  pricewatches table `db`."
  [db k v]
  (let [watches (watches-for-product db k)
        matching-watches (filter #(pricewatch-match? v %) watches)]
    (for [watch matching-watches]
      (act-on-pricewatch-match! v watch))))

(defn do-watches!-fn
  "Returns a configured do-watches! function configured with a pricewatch db."
  [db]
  (fn [[k v]] (do-watches! db k v)))

(defn topology-builder-fn
  "Returns a function that applies topology DSL to the provided builder."
  [opts]
  (let [topic-configs (get-in opts [:topic-registry :topic-configs])
        {:keys [prices-by-id pricewatch-matches]} topic-configs]
    (fn [builder]
      (-> builder
          (streams/kstream prices-by-id)
          (streams/peek (do-watches!-fn watches-table))
          (streams/to pricewatch-matches))
      builder)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Integrant methods
;;;;

(defmethod ig/init-key :reaction.pricewatch.processor/topology-builder-fn
  [_ opts]
  topology-builder-fn)

(defmethod ig/halt-key! :reaction.pricewatch.processor/topology-builder-fn
  [_ opts]
  nil)

(defmethod ig/init-key :reaction.pricewatch/processor
  [_ opts]
  (-> opts processor/map->Processor start))

(defmethod ig/halt-key! :reaction.pricewatch/processor
  [_ this]
  (stop this))

(defmethod ig/halt-key! :reaction.pricewatch/processor
  [_ this]
  (stop this))

(defmethod ig/init-key :reaction.pricewatch/mock-processor
  [_ opts]
  (-> opts processor/map->MockProcessor start))

(defmethod ig/halt-key! :reaction.pricewatch/mock-processor
  [_ this]
  (stop this))
