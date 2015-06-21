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

  case class StructEvent(
    category: String,
    action: String,
    label: Option[String],
    property: Option[String],
    value: Option[Double]) extends Props

  case class UnstructEvent(json: SelfDescribingJson) extends Props

  case class PageView(
    pageUri: String,
    pageTitle: Option[String],
    referrer: Option[String]) extends Props

  case class ECommerceTrans(
    orderId: String,
    totalValue: Double,
    affiliation: Option[String],
    taxValue: Option[Double],
    shipping: Option[Double],
    city: Option[String],
    state: Option[String],
    country: Option[String],
    currency: Option[String],
    transactionItems: Option[Seq[TransactionItem]]) extends Props

  case class TransactionItem(
    sku: String,
    price: Double,
    quantity: Double,
    name: Option[String],
    category: Option[String])
}

trait Tracker {
  import Tracker._

  def trackUnstructuredEvent(events: UnstructEvent)(implicit subject: Option[Subject] = None, timestamp: Option[Long] = None)

  def trackStructuredEvent(events: StructEvent)(implicit subject: Option[Subject] = None, timestamp: Option[Long] = None)

  def trackPageView(pageView: PageView)(implicit subject: Option[Subject] = None, timestamp: Option[Long] = None)

  def trackECommerceTransaction(trans: ECommerceTrans)(implicit subject: Option[Subject] = None, timestamp: Option[Long] = None)
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
import com.typesafe.config.ConfigFactory

class TrackerImpl(emitters: Seq[ActorRef], subject: Option[Subject] = None, contexts: Seq[SelfDescribingJson] = Nil)(implicit attr: Attributes)
  extends Tracker {

  import Tracker._
  import TrackerImpl._
  import com.snowplowanalytics.snowplow.scalatracker.emitters.Emitter._
  import com.snowplowanalytics.snowplow.scalatracker.SelfDescribingJson
  import akka.actor.ActorSystem
  import akka.event.Logging

  val log = Logging.getLogger(ActorSystem("Tracker-Logging", ConfigFactory.load.getConfig("akka")), this)

  override def trackStructuredEvent(se: StructEvent)(implicit subject: Option[Subject] = None, timestamp: Option[Long] = None) = {
    val payload: Payload = Map(EVENT -> Constants.EVENT_STRUCTURED)

    payload += (SE_CATEGORY -> se.category)
    payload += (SE_ACTION -> se.action)
    payload += (SE_LABEL -> se.label.getOrElse(""))
    payload += (SE_PROPERTY -> se.property.getOrElse(""))
    payload += (SE_VALUE -> se.value.getOrElse(0.0).toString)

    emitters foreach (_ ! completePayload(payload)(subject, timestamp))
  }
  override def trackECommerceTransaction(trans: ECommerceTrans)(implicit subject: Option[Subject] = None, timestamp: Option[Long] = None) = {
    val payload: Payload = Map(EVENT -> Constants.EVENT_ECOMM)

    payload += (TR_ID -> trans.orderId)
    payload += (TR_TOTAL -> trans.totalValue.toString)
    payload += (TR_AFFILIATION -> trans.affiliation.getOrElse(""))
    payload += (TR_TAX -> trans.taxValue.getOrElse(0.0).toString)
    payload += (TR_SHIPPING -> trans.shipping.getOrElse(0.0).toString)
    payload += (TR_CITY -> trans.city.getOrElse(""))
    payload += (TR_STATE -> trans.state.getOrElse(""))
    payload += (TR_COUNTRY -> trans.country.getOrElse(""))
    payload += (TR_CURRENCY -> trans.currency.getOrElse(""))

    // transaction item here

    emitters foreach { _ ! completePayload(payload)(subject, timestamp) }
  }

  override def trackPageView(pageView: PageView)(implicit subject: Option[Subject] = None, timestamp: Option[Long] = None) = {
    val payload: Payload = Map(EVENT -> Constants.EVENT_PAGE_VIEW)

    payload += (PAGE_URL -> pageView.pageUri)
    payload += (PAGE_TITLE -> pageView.pageTitle.getOrElse(""))
    payload += (PAGE_REFR -> pageView.referrer.getOrElse(""))

    emitters foreach { _ ! completePayload(payload)(subject, timestamp) }
  }

  override def trackUnstructuredEvent(unstructEvent: UnstructEvent)(implicit subject: Option[Subject] = None, timestamp: Option[Long] = None) {

    val payload: Payload = Map(EVENT -> Constants.EVENT_UNSTRUCTURED)

    val envelope = SelfDescribingJson(
      Constants.SCHEMA_UNSTRUCT_EVENT,
      unstructEvent.json)

    val jsonString = compact(render(envelope.toJObject))

    payload.addJson(jsonString, attr.encodeBase64, which = (UNSTRUCTURED_ENCODED, UNSTRUCTURED))

    // send message to our emitter actors
    emitters foreach { _ ! completePayload(payload)(subject, timestamp) }
  }

  private def completePayload(payload: Payload)(subject: Option[Subject], timestamp: Option[Long]): Payload = {
    payload += (PLATFORM -> Server.abbreviation)
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

    val info: scala.collection.Map[String, String] = (this.subject, subject) match {
      case (Some(ins: Subject), Some(ins2: Subject)) => ins.getSubjectInformation() ++ ins2.getSubjectInformation()
      case (None, Some(ins2: Subject)) => ins2.getSubjectInformation()
      case (Some(ins: Subject), None) => ins.getSubjectInformation()
      case (None, None) => Map.empty
    }
    payload ++= info

    payload
  }
}
