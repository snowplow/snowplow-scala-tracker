/*
 * Copyright (c) 2015-2018 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.scalatracker

import cats._
import cats.data.NonEmptyList
import cats.implicits._
import cats.effect.Clock
import io.circe.Json
import io.circe.syntax._
import com.snowplowanalytics.iglu.core.{SchemaKey, SchemaVer, SelfDescribingData}
import com.snowplowanalytics.iglu.core.circe.implicits._
import utils.{ErrorTracking, JsonUtils}

import scala.concurrent.duration._

/**
 * Tracker class
 *
 * @param emitters Sequence of emitters to which events are passed
 * @param namespace Tracker namespace
 * @param appId ID of the application
 * @param encodeBase64 Whether to encode JSONs
 * @param metadata optionally a json containing the metadata context for the running instance
 */
final case class Tracker[F[_]: Monad: Clock: UUIDProvider](emitters: NonEmptyList[Emitter[F]],
                                                           namespace: String,
                                                           appId: String,
                                                           subject: Subject                     = Subject(),
                                                           encodeBase64: Boolean                = true,
                                                           metadata: Option[SelfDescribingJson] = None) {
  import Tracker._

  /**
   * Send assembled payload to emitters or schedule it as callback of getting context
   *
   * @param payload constructed event map
   */
  private def track(payload: Payload): F[Unit] = {
    val payloadToSend = metadata
      .map(context => addContexts(payload, List(context)))
      .getOrElse(payload)

    send(payloadToSend)
  }

  /**
   * Pass the assembled payload to every emitter
   *
   * @param payload constructed event map
   */
  private def send(payload: Payload): F[Unit] = {
    val event = payload.get
    emitters.traverse(e => e.send(event)).map(x => ())
  }

  /**
   * Add contexts and timestamp to the payload
   *
   * @param payload constructed event map
   * @param contexts list of additional contexts
   * @param timestamp optional user-provided timestamp (ms) for the event
   * @return payload with additional data
   */
  private def completePayload(payload: Payload,
                              contexts: Seq[SelfDescribingJson],
                              timestamp: Option[Timestamp]): F[Payload] =
    for {
      uuid   <- implicitly[UUIDProvider[F]].generateUUID
      millis <- implicitly[Clock[F]].realTime(MILLISECONDS)
    } yield {
      val newPayload = payload
        .add("eid", uuid.toString)
        .add("tv", Version)
        .add("tna", namespace)
        .add("aid", appId)
        .addDict(subject.subjectInformation)

      val payloadWithTimestamp = if (!newPayload.get.contains("dtm")) {
        timestamp match {
          case Some(DeviceCreatedTimestamp(dtm)) => newPayload.add("dtm", dtm.toString)
          case Some(TrueTimestamp(ttm))          => newPayload.add("ttm", ttm.toString)
          case None                              => newPayload.add("dtm", millis.toString)
        }
      } else {
        newPayload
      }

      addContexts(payloadWithTimestamp, contexts)
    }

  /**
   * Add contexts to the payload or return same payload
   *
   * @param payload constructed event map
   * @param contexts list of additional contexts
   * @return payload with contexts
   */
  private def addContexts(payload: Payload, contexts: Seq[SelfDescribingJson]): Payload =
    if (contexts.nonEmpty) {
      val contextsEnvelope: SelfDescribingJson =
        SelfDescribingData(ContextsSchemaKey, Json.fromValues(contexts.toIterable.map(_.normalize)))

      payload.addJson(contextsEnvelope.normalize, encodeBase64, "cx", "co")
    } else {
      payload
    }

  /**
   * Track a Snowplow self-describing event
   *
   * @param unstructEvent self-describing JSON for the event
   * @param contexts list of additional contexts
   * @param timestamp optional user-provided timestamp (ms) for the event
   * @return The tracker instance
   */
  def trackSelfDescribingEvent(unstructEvent: SelfDescribingJson,
                               contexts: Seq[SelfDescribingJson] = Nil,
                               timestamp: Option[Timestamp]      = None): F[Unit] = {

    val envelope = SelfDescribingData(SelfDescribingEventSchemaKey, unstructEvent.normalize)
    val payload = Payload()
      .add("e", "ue")
      .addJson(envelope.normalize, encodeBase64, "ue_px", "ue_pr")

    completePayload(payload, contexts, timestamp)
      .flatMap(track)
  }

  /**
   * Track a Snowplow structured event
   *
   * @param category event category mapped to se_ca
   * @param action event itself mapped to se_ac
   * @param label optional object label mapped to se_la
   * @param property optional event/object property mapped to se_pr
   * @param value optional object value mapped to se_va
   * @param contexts list of additional contexts
   * @param timestamp optional user-provided timestamp (ms) for the event
   * @return the tracker instance
   */
  def trackStructEvent(category: String,
                       action: String,
                       label: Option[String]             = None,
                       property: Option[String]          = None,
                       value: Option[Double]             = None,
                       contexts: Seq[SelfDescribingJson] = Nil,
                       timestamp: Option[Timestamp]      = None): F[Unit] = {

    val payload = Payload()
      .add("e", "se")
      .add("se_ca", category)
      .add("se_ac", action)
      .add("se_la", label)
      .add("se_pr", property)
      .add("se_va", value.map(_.toString))

    completePayload(payload, contexts, timestamp)
      .flatMap(track)
  }

  /**
   * Record view of web page
   *
   * @param pageUrl viewed URL
   * @param pageTitle page's title
   * @param referrer referrer URL
   * @param contexts list of additional contexts
   * @param timestamp optional user-provided timestamp (ms) for the event
   * @return the tracker instance
   */
  def trackPageView(pageUrl: String,
                    pageTitle: Option[String]         = None,
                    referrer: Option[String]          = None,
                    contexts: Seq[SelfDescribingJson] = Nil,
                    timestamp: Option[Timestamp]      = None): F[Unit] = {

    val payload = Payload()
      .add("e", "pv")
      .add("url", pageUrl)
      .add("page", pageTitle)
      .add("refr", referrer)

    completePayload(payload, contexts, timestamp)
      .flatMap(track)
  }

  /**
   * Record view of transaction
   *
   * @param orderId Order ID
   * @param affiliation Transaction affiliation
   * @param total Total transaction value
   * @param tax Total tax included in transaction value
   * @param shipping Delivery cost charged
   * @param city Delivery address, city
   * @param state Delivery address, state
   * @param country Delivery address, country
   * @param currency Currency
   * @param contexts list of additional contexts
   * @param timestamp optional user-provided timestamp (ms) for the event
   * @return the tracker instance
   */
  def trackTransaction(orderId: String,
                       affiliation: Option[String] = None,
                       total: Double,
                       tax: Option[Double]               = None,
                       shipping: Option[Double]          = None,
                       city: Option[String]              = None,
                       state: Option[String]             = None,
                       country: Option[String]           = None,
                       currency: Option[String]          = None,
                       contexts: Seq[SelfDescribingJson] = Nil,
                       timestamp: Option[Timestamp]      = None): F[Unit] = {

    val payload = Payload()
      .add("e", "tr")
      .add("tr_id", orderId)
      .add("tr_af", affiliation)
      .add("tr_tt", total.toString)
      .add("tr_tx", tax.map(_.toString))
      .add("tr_sh", shipping.map(_.toString))
      .add("tr_ci", city)
      .add("tr_st", state)
      .add("tr_co", country)
      .add("tr_cu", currency)

    completePayload(payload, contexts, timestamp)
      .flatMap(track)
  }

  /**
   * @param orderId Order ID
   * @param sku Product SKU
   * @param name Product name
   * @param category Product category
   * @param price Product unit price
   * @param quantity Number of product in transaction
   * @param currency The currency the price is expressed in
   * @param contexts Custom context relating to the event
   * @param timestamp optional user-provided timestamp (ms) for the event
   * @return the tracker instance
   */
  def trackTransactionItem(orderId: String,
                           sku: String,
                           name: Option[String]     = None,
                           category: Option[String] = None,
                           price: Double,
                           quantity: Int,
                           currency: Option[String]           = None,
                           contexts: List[SelfDescribingJson] = Nil,
                           timestamp: Option[Timestamp]       = None): F[Unit] = {

    val payload = Payload()
      .add("e", "ti")
      .add("ti_id", orderId)
      .add("ti_sk", sku)
      .add("ti_nm", name)
      .add("ti_ca", category)
      .add("ti_pr", price.toString)
      .add("ti_qu", quantity.toString)
      .add("ti_cu", currency)

    completePayload(payload, contexts, timestamp)
      .flatMap(track)
  }

  /**
   * Track an add-to-cart event
   *
   * @param sku Required. Item's SKU code.
   * @param name Optional. Product name.
   * @param category Optional. Product category.
   * @param unitPrice Optional. Product price.
   * @param quantity Required. Quantity added.
   * @param currency Optional. Product price currency.
   * @param contexts Optional. Context relating to the event.
   * @param timestamp optional user-provided timestamp (ms) for the event
   * @return the tracker instance
   */
  def trackAddToCart(sku: String,
                     name: Option[String]      = None,
                     category: Option[String]  = None,
                     unitPrice: Option[Double] = None,
                     quantity: Int,
                     currency: Option[String]           = None,
                     contexts: List[SelfDescribingJson] = Nil,
                     timestamp: Option[Timestamp]       = None): F[Unit] = {

    val eventJson = JsonUtils.jsonObjectWithoutNulls(
      "sku" := sku,
      "name" := name,
      "category" := category,
      "unitPrice" := unitPrice,
      "quantity" := quantity,
      "currency" := currency
    )

    trackSelfDescribingEvent(
      SelfDescribingData(
        SchemaKey("com.snowplowanalytics.snowplow", "add_to_cart", "jsonschema", SchemaVer.Full(1, 0, 0)),
        eventJson),
      contexts,
      timestamp)
  }

  /**
   * Track a remove-from-cart event
   *
   * @param sku Required. Item's SKU code.
   * @param name Optional. Product name.
   * @param category Optional. Product category.
   * @param unitPrice Optional. Product price.
   * @param quantity Required. Quantity removed.
   * @param currency Optional. Product price currency.
   * @param contexts Optional. Context relating to the event.
   * @param timestamp optional user-provided timestamp (ms) for the event
   * @return the tracker instance
   */
  def trackRemoveFromCart(sku: String,
                          name: Option[String]      = None,
                          category: Option[String]  = None,
                          unitPrice: Option[Double] = None,
                          quantity: Double,
                          currency: Option[String]           = None,
                          contexts: List[SelfDescribingJson] = Nil,
                          timestamp: Option[Timestamp]       = None): F[Unit] = {

    val eventJson = JsonUtils.jsonObjectWithoutNulls(
      "sku" := sku,
      "name" := name,
      "category" := category,
      "unitPrice" := unitPrice,
      "quantity" := quantity,
      "currency" := currency
    )

    trackSelfDescribingEvent(
      SelfDescribingData(
        SchemaKey("com.snowplowanalytics.snowplow", "remove_from_cart", "jsonschema", SchemaVer.Full(1, 0, 0)),
        eventJson),
      contexts,
      timestamp)
  }

  /**
   * Track application error
   * NOTE: don't try to tracker `Emitter` failures with it
   *
   * @param error exception thrown by application code
   * @param contexts Optional. Context relating to the event.
   * @param timestamp optional user-provided timestamp (ms) for the event
   * @return the tracker instance
   */
  def trackError(error: Throwable,
                 contexts: List[SelfDescribingJson] = Nil,
                 timestamp: Option[Timestamp]       = None): F[Unit] = {
    val payload = ErrorTracking.toData(error)
    val event   = SelfDescribingData(ErrorTracking.ApplicationErrorSchemaKey, payload)
    trackSelfDescribingEvent(event, contexts, timestamp)
  }

  /**
   * Set the Subject for the tracker
   * The subject's configuration will be attached to every event
   *
   * @param newSubject user which the Tracker will track
   * @return The tracker instance
   */
  def setSubject(newSubject: Subject): Tracker[F] =
    new Tracker[F](emitters, namespace, appId, newSubject, encodeBase64, metadata)
}

object Tracker {

  def apply[F[_]: Monad: Clock: UUIDProvider](emitter: Emitter[F], namespace: String, appId: String): Tracker[F] =
    Tracker(NonEmptyList.one(emitter), namespace, appId)

  /** Tracker's version */
  val Version = s"scala-${generated.ProjectSettings.version}"

  /** Contexts wrapper */
  val ContextsSchemaKey: SchemaKey =
    SchemaKey("com.snowplowanalytics.snowplow", "contexts", "jsonschema", SchemaVer.Full(1, 0, 1))

  /** Unstruct event wrapper */
  val SelfDescribingEventSchemaKey: SchemaKey =
    SchemaKey("com.snowplowanalytics.snowplow", "unstruct_event", "jsonschema", SchemaVer.Full(1, 0, 0))

  /** POST-payload wrapper */
  val PayloadDataSchemaKey: SchemaKey =
    SchemaKey("com.snowplowanalytics.snowplow", "payload_data", "jsonschema", SchemaVer.Full(1, 0, 4))

  /**
   * Tag-type for timestamp, allowing to set ttm/dtm
   */
  sealed trait Timestamp { val value: Long }
  case class TrueTimestamp(value: Long) extends Timestamp
  case class DeviceCreatedTimestamp(value: Long) extends Timestamp

  /**
   * Implicit conversion of Long values to [[DeviceCreatedTimestamp]] as default
   */
  implicit def longToTimestamp(value: Long): Timestamp =
    DeviceCreatedTimestamp(value)
}
