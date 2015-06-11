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

  // SCHEMA URIs

  val UE_SCHEMA_URI = "iglu:com.snowplowanalytics.snowplow/unstruct_event/jsonschema/1-0-0"
  val CONTEXT_SCHEMA_URI = "iglu:com.snowplowanalytics.snowplow/contexts/jsonschema/1-0-1"

}

trait Tracker {
  import Tracker._

  def trackUnstructEvent(events: UnstructEvent)

  def trackStructEvent(events: StructEvent)

  def trackPageView(pageView: PageView)

  def trackECommerceTransaction(trans: ECommerceTrans)
}

object UETracker {

  val Version = s"scala-${generated.ProjectSettings.version}"
  case class Attributes(namespace: String, appId: String, encodeBase64: Boolean = true)

  def getTimestamp(timestamp: Option[Long]): Long = timestamp match {
    case None => System.currentTimeMillis
    case Some(t) => t * 1000
  }
}

import UETracker._

class UETracker(emitters: Seq[ActorRef], subject: Subject)(implicit attr: Attributes, contexts: Seq[SelfDescribingJson], timestamp: Option[Long])
  extends Tracker {

  import Tracker._
  import UETracker._
  import com.snowplowanalytics.snowplow.scalatracker.emitters.Emitter._
  import com.snowplowanalytics.snowplow.scalatracker.SelfDescribingJson

  def trackStructEvent(se: StructEvent) = notSupported("Structured event tracking is not supported")
  def trackECommerceTransaction(trans: ECommerceTrans) = notSupported("E-commerce transaction tracking is not supported")
  def trackPageView(pageView: PageView) = notSupported("Page view tracking is not supported")

  override def trackUnstructEvent(unstructEvent: UnstructEvent) {

    val payload: Payload = Map("e" -> "ue")

    val envelope = SelfDescribingJson(
      UE_SCHEMA_URI,
      unstructEvent.json)

    val jsonString = compact(render(envelope.toJObject))

    payload.addJson(jsonString, attr.encodeBase64, which = (UE_PX, UE_PR))

    // send message to our emitter actors
    emitters foreach (_ ! fillPayload(payload))
  }

  private def fillPayload(payload: Payload): Payload = {
    payload += ("eid" -> UUID.randomUUID().toString)

    if (!contexts.isEmpty) {
      val contextsEnvelope = SelfDescribingJson(
        CONTEXT_SCHEMA_URI,
        contexts)

      val jsonString = compact(render(contextsEnvelope.toJObject))

      payload.addJson(jsonString, attr.encodeBase64, which = ("cx", "co"))
    }

    if (!payload.contains("dtm")) {
      payload += ("dtm" -> getTimestamp(timestamp).toString)
    }

    payload += ("tv" -> Version)
    payload += ("tna" -> attr.namespace)
    payload += ("aid" -> attr.appId)

    payload ++= subject.getSubjectInformation()

    payload
  }
}
