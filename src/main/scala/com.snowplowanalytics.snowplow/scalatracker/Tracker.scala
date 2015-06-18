/*
 * Copyright (c) 2015 Snowplow Analytics Ltd. All rights reserved.
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

import java.util.UUID

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import akka.actor.{ ActorRef }
import scala.collection.mutable.Map

object Tracker {

  sealed trait Props
  case class StructEvent(category: String,
    action: String,
    label: Option[String],
    property: Option[String],
    value: Option[Long]) extends Props
  case class UnstructEvent(json: SelfDescribingJson) extends Props
  case class PageView(pageUri: String,
    pageTitle: Option[String],
    referrer: Option[String]) extends Props
  case class ECommerceTrans(orderId: String,
    totalValue: Long,
    affiliation: Option[String],
    taxValue: Option[Long],
    shipping: Option[Long],
    city: Option[String],
    state: Option[String],
    country: Option[String],
    currency: Option[String],
    transactionItems: Option[Seq[TransactionItem]]) extends Props
  case class TransactionItem(sku: String,
    price: Long,
    quantity: Long,
    name: Option[String],
    category: Option[String])
}

trait Tracker {
  import Tracker._

  def trackUnstructuredEvent(events: UnstructEvent)

  def trackStructuredEvent(events: StructEvent)

  def trackPageView(pageView: PageView)

  def trackECommerceTransaction(trans: ECommerceTrans)
}

object TrackerImpl {

  val Version = s"scala-${generated.ProjectSettings.version}"
  case class Attributes(namespace: String, appId: String, encodeBase64: Boolean = true)

  def getTimestamp(timestamp: Option[Long]): Long = timestamp match {
    case None => System.currentTimeMillis
    case Some(t) => t * 1000
  }
}

import TrackerImpl._

class TrackerImpl(emitters: Seq[ActorRef], subject: Option[Subject] = None)(implicit attr: Attributes, contexts: Seq[SelfDescribingJson], timestamp: Option[Long])
  extends Tracker {

  import Tracker._
  import TrackerImpl._
  import com.snowplowanalytics.snowplow.scalatracker.emitters.Emitter._
  import com.snowplowanalytics.snowplow.scalatracker.SelfDescribingJson

  override def trackStructuredEvent(se: StructEvent) = {
    val payload: Payload = Map(EVENT -> Constants.EVENT_STRUCTURED)
    payload += (SE_CATEGORY -> se.category)
    payload += (SE_ACTION -> se.action)
    payload += (SE_LABEL -> se.label.getOrElse(""))
    payload += (SE_PROPERTY -> se.property.getOrElse(""))
    payload += (SE_VALUE -> se.value.get.toString)

    emitters foreach (_ ! completePayload(payload))
  }
  def trackECommerceTransaction(trans: ECommerceTrans) = notSupported("E-commerce transaction tracking is not supported")
  def trackPageView(pageView: PageView) = notSupported("Page view tracking is not supported")

  override def trackUnstructuredEvent(unstructEvent: UnstructEvent) {

    val payload: Payload = Map(EVENT -> Constants.EVENT_UNSTRUCTURED)

    val envelope = SelfDescribingJson(
      Constants.SCHEMA_UNSTRUCT_EVENT,
      unstructEvent.json)

    val jsonString = compact(render(envelope.toJObject))

    payload.addJson(jsonString, attr.encodeBase64, which = (UNSTRUCTURED_ENCODED, UNSTRUCTURED))

    // send message to our emitter actors
    emitters foreach (_ ! completePayload(payload))
  }

  private def completePayload(payload: Payload): Payload = {
    payload += (EID -> UUID.randomUUID().toString)

    if (!contexts.isEmpty) {
      val contextsEnvelope = SelfDescribingJson(
        Constants.SCHEMA_CONTEXTS,
        contexts)

      val jsonString = compact(render(contextsEnvelope.toJObject))

      payload.addJson(jsonString, attr.encodeBase64, which = (CONTEXT_ENCODED, CONTEXT))
    }

    if (!payload.contains(TIMESTAMP)) {
      payload += (TIMESTAMP -> getTimestamp(timestamp).toString)
    }

    payload += (TRACKER_VERSION -> Version)
    payload += (NAMESPACE -> attr.namespace)
    payload += (APPID -> attr.appId)

    payload ++= subject.get.getSubjectInformation()

    payload
  }
}
