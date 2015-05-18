package com.snowplowanalytics.snowplow.scalatracker

import java.util.UUID

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import emitters.TEmitter

class Tracker(emitters: Seq[TEmitter], namespace: String, appId: String, encodeBase64: Boolean = true) {

  private val Version = generated.ProjectSettings.version

  private def getTimestamp(timestamp: Option[Long]): Long = timestamp match {
    case None => System.currentTimeMillis()
    case Some(t) => t * 1000
  }

  private def track(payload: Payload) {
    val event = payload.get
    emitters foreach {
      e => e.input(event)
    }
  }

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
      payload.add("dtm", getTimestamp(timestamp).toString)
    }

    payload.add("tv", Version)
    payload.add("tna", namespace)
    payload.add("aid", appId)

    payload
  }

  def trackUnstructEvent(
    unstructEvent: SelfDescribingJson,
    contexts: Seq[SelfDescribingJson],
    timestamp: Option[Long] = None): Tracker = {

    val payload = new Payload()

    val envelope = SelfDescribingJson(
      "iglu:com.snowplowanalytics.snowplow/unstruct_event/jsonschema/1-0-0",
      unstructEvent)

    payload.addJson(envelope.toJObject, encodeBase64, "ue_px", "ue_pr")

    track(completePayload(payload, contexts, timestamp))

    this
  }
}
