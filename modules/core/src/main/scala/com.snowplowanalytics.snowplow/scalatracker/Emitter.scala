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

import java.util.UUID

/**
 * This trait is needed here because `Tracker` is referentially transparent, and we leave it up for the emitters
 * to decide whether `F` will be referentially transparent also.
 */
trait UUIDProvider[F[_]] {

  /**
   * Returns a random UUID. Most likely it will be `UUID.randomUUID()` wrapped in `F`
   * @return current time in milliseconds
   */
  def generateUUID: F[UUID]
}

/**
 * Emitters are entities in charge of transforming events sent from tracker
 * into actual HTTP requests (IO), which includes:
 * + Async/Multi-threading
 * + Queuing `EmitterPayload`
 * + Transforming `EmitterPayload` into Bytes
 * + Backup queue and callbacks
 *
 */
trait Emitter[F[_]] {

  /**
   * Emits the event payload
   *
   * @param event Fully assembled event
   */
  def send(event: Emitter.EmitterPayload): F[Unit]

}

object Emitter {

  /** Low-level representation of event */
  type EmitterPayload = Map[String, String]

  /** ADT for possible track results */
  sealed trait CollectorResponse extends Product with Serializable

  /** Success. Collector accepted an event */
  final case class CollectorSuccess(code: Int) extends CollectorResponse

  /** Collector refused an event. Probably wrong endpoint or outage */
  final case class CollectorFailure(code: Int) extends CollectorResponse

  /** Other failure. Timeout or network unavailability */
  final case class TrackerFailure(throwable: Throwable) extends CollectorResponse

  /** Emitter cannot continue retrying. Note that this is not *response* */
  final case class RetriesExceeded(lastResponse: CollectorResponse) extends CollectorResponse

  /** User-provided callback */
  type Callback[F[_]] = (CollectorParams, CollectorRequest, CollectorResponse) => F[Unit]

  /** Payload (either GET or POST) ready to be send to collector */
  sealed trait CollectorRequest extends Product with Serializable {

    /** Attempt to send */
    def attempt: Int

    /** Check if emitters should keep sending this request */
    def isFailed: Boolean = attempt >= 10

    /** Increment attempt number. Must be used whenever payload failed */
    def updateAttempt: CollectorRequest = this match {
      case g: GetCollectorRequest  => g.copy(attempt = attempt + 1)
      case p: PostCollectorRequest => p.copy(attempt = attempt + 1)
    }

    /**
     * Return same payload, but with updated stm
     * **Must** be used right before payload goes to collector
     */
    def updateStm: CollectorRequest = this match {
      case GetCollectorRequest(_, map) =>
        val stm = System.currentTimeMillis().toString
        GetCollectorRequest(attempt, map.updated("stm", stm))
      case PostCollectorRequest(_, list) =>
        val stm = System.currentTimeMillis().toString
        PostCollectorRequest(attempt, list.map(_.updated("stm", stm)))
    }
  }

  /** Single event, supposed to passed with GET-request */
  final case class GetCollectorRequest(attempt: Int, payload: EmitterPayload) extends CollectorRequest

  /** Multiple events, supposed to passed with POST-request */
  final case class PostCollectorRequest(attempt: Int, payload: List[EmitterPayload]) extends CollectorRequest

  /** Collector preferences */
  case class CollectorParams(host: String, port: Int, https: Boolean) {

    /** Return stringified collector representation, e.g. `https://splw.acme.com:80/` */
    def getUri: String = s"${if (https) "https" else "http"}://$host:$port"
  }

  object CollectorParams {

    /** Construct collector preferences with correct default port */
    def construct(host: String, port: Option[Int] = None, https: Boolean = false): CollectorParams =
      port match {
        case Some(p)       => CollectorParams(host, p, https)
        case None if https => CollectorParams(host, 443, https = true)
        case None          => CollectorParams(host, 80, https = false)
      }
  }
}
