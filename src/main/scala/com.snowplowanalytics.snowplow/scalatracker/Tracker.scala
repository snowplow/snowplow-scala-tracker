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

import emitters.TEmitter

/**
 * Tracker class
 *
 * @param emitters Sequence of emitters to which events are passed
 * @param namespace Tracker namespace
 * @param appId ID of the application
 * @param encodeBase64 Whether to encode JSONs
 */
class Tracker(emitters: Seq[TEmitter], namespace: String, appId: String, encodeBase64: Boolean = true) {

  private val Version = s"scala-${generated.ProjectSettings.version}"

  private var subject: Subject = new Subject()

  /**
   * Pass the assembled payload to every emitter
   *
   * @param payload constructed event map
   */
  private def track(payload: Payload) {
    val event = payload.get
    emitters foreach {
      e => e.input(event)
    }
  }

  /**
   * Add contexts and timestamp to the payload
   *
   * @param payload constructed event map
   * @param contexts list of additional contexts
   * @param timestamp optional user-provided timestamp (ms) for the event
   * @return payload with additional data
   */
  private def completePayload(
    payload: Payload,
    contexts: Seq[SelfDescribingJson],
    timestamp: Option[Long]): Payload = {

    payload.add("eid", UUID.randomUUID().toString)

    if (! contexts.isEmpty) {

      val contextsEnvelope = SelfDescribingJson(
        "iglu:com.snowplowanalytics.snowplow/contexts/jsonschema/1-0-1",
        contexts
        )

      payload.addJson(contextsEnvelope.toJObject, encodeBase64, "cx", "co")
    }

    if (!payload.nvPairs.contains("dtm")) {
      timestamp match {
        case Some(dtm) => payload.add("dtm", dtm.toString)
        case None =>      payload.add("dtm", System.currentTimeMillis().toString)
      }
    }

    payload.add("tv", Version)
    payload.add("tna", namespace)
    payload.add("aid", appId)

    payload.addDict(subject.getSubjectInformation())

    payload
  }

  /**
   * Track a Snowplow unstructured event
   *
   * @param unstructEvent self-describing JSON for the event
   * @param contexts list of additional contexts
   * @param timestamp optional user-provided timestamp (ms) for the event
   * @return The tracker instance
   */
  def trackUnstructEvent(
    unstructEvent: SelfDescribingJson,
    contexts: Seq[SelfDescribingJson] = Nil,
    timestamp: Option[Long] = None): Tracker = {

    val payload = new Payload()

    payload.add("e", "ue")

    val envelope = SelfDescribingJson(
      "iglu:com.snowplowanalytics.snowplow/unstruct_event/jsonschema/1-0-0",
      unstructEvent)

    payload.addJson(envelope.toJObject, encodeBase64, "ue_px", "ue_pr")

    track(completePayload(payload, contexts, timestamp))

    this
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
  def trackStructEvent(
    category: String,
    action: String,
    label: Option[String] = None,
    property: Option[String] = None,
    value: Option[Double] = None,
    contexts: Seq[SelfDescribingJson] = Nil,
    timestamp: Option[Long] = None): Tracker = {

    val payload = new Payload()

    payload.add("e", "se")
    payload.add("se_ca", category)
    payload.add("se_ac", action)
    payload.add("se_la", label)
    payload.add("se_pr", property)
    payload.add("se_va", value.map(_.toString))

    track(completePayload(payload, contexts, timestamp))

    this
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
  def trackPageView(
    pageUrl: String,
    pageTitle: Option[String] = None,
    referrer: Option[String] = None,
    contexts: Seq[SelfDescribingJson] = Nil,
    timestamp: Option[Long] = None): Tracker = {

    val payload = new Payload()

    payload.add("e", "pv")
    payload.add("url", pageUrl)
    payload.add("page", pageTitle)
    payload.add("refr", referrer)

    track(completePayload(payload, contexts, timestamp))

    this
  }

  /**
   * Set the Subject for the tracker
   * The subject's configuration will be attached to every event
   *
   * @param subject user which the Tracker will track
   * @return The tracker instance
   */
  def setSubject(subject: Subject): Tracker = {
    this.subject = subject
    this
  }
}
