/*
 * Copyright (c) 2015-2020 Snowplow Analytics Ltd. All rights reserved.
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

import java.net.URI

import cats.implicits._

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
  def send(event: Payload): F[Unit]

}

object Emitter {

  /** User-provided callback */
  type Callback[F[_]] = (EndpointParams, Request, Result) => F[Unit]

  /** Emitter buffering config */
  sealed trait BufferConfig extends Product with Serializable

  object BufferConfig {

    /** Configures the emitter to send events immediately to the collector, without buffering for larger batches */
    case object NoBuffering extends BufferConfig

    /** Configures the emitter to buffer events and send batched payloads comprising a fixed number of events
     *  @param size The number of events after which the emitter sends the buffered events to the collector
     */
    final case class EventsCardinality(size: Int) extends BufferConfig

    /** Configures the emitter to buffer events and send batched payloads of a minimum size in bytes
     *  @param bytes The target minimum size in bytes of a payload of batched events
     */
    final case class PayloadSize(bytes: Int) extends BufferConfig

    /** Configures the emitter to buffer events until either of the inner BufferConfigs say the queue is full.  */
    final case class OneOf(left: BufferConfig, right: BufferConfig) extends BufferConfig
  }

  /** Payload (either GET or POST) ready to be send to collector */
  sealed trait Request extends Product with Serializable {

    /** A counter of how many attempts are made to send a payload.
     *
     *  This is a 1-based counter, so if `attempt == 1` then this represents the first and only attempt to send.
     */
    def attempt: Int

    /** Check if emitters should keep sending this request */
    def isFailed: Boolean = attempt >= 10

    /** Increment attempt number. Must be used whenever payload failed */
    def updateAttempt: Request = this match {
      case g: Request.Single   => g.copy(attempt = attempt + 1)
      case p: Request.Buffered => p.copy(attempt = attempt + 1)
    }

    /**
     * Return same payload, but with updated stm
     * Must be used right before payload goes to collector
     */
    def updateStm(deviceSentTimestamp: Long): Request = {
      val stm = deviceSentTimestamp.toString
      this match {
        case Request.Single(_, payload) =>
          Request.Single(attempt, payload.add("stm", stm))
        case Request.Buffered(_, list) =>
          Request.Buffered(attempt, list.map(_.add("stm", stm)))
      }
    }
  }

  object Request {

    /** Single event, supposed to passed with GET-request */
    final case class Single(attempt: Int, payload: Payload) extends Request

    /** Multiple events, supposed to passed with POST-request */
    final case class Buffered(attempt: Int, payload: List[Payload]) extends Request

    /** Initial GET */
    private[scalatracker] def apply(payload: Payload): Request =
      Single(1, payload)

    /** Initial POST */
    private[scalatracker] def apply(payload: List[Payload]): Request =
      Buffered(1, payload)
  }

  /** Collector preferences */
  final case class EndpointParams(host: String, port: Int, https: Boolean) {

    /** Return stringified collector representation, e.g. `https://splw.acme.com:80/` */
    def getUri: String = s"${if (https) "https" else "http"}://$host:$port"

    def getPostUri: String = getUri ++ "/com.snowplowanalytics.snowplow/tp2"

    def getGetUri: String = getUri ++ "/i"
  }

  object EndpointParams {

    /** Construct collector preferences with correct default port */
    def apply(host: String, port: Option[Int] = None, https: Boolean = false): EndpointParams =
      EndpointParams(host, port.getOrElse(if (https) 443 else 80), https)

    /** Construct collector preferences from URI, fail on any unexpected element */
    def fromUri(uri: URI): Either[String, EndpointParams] = {
      val https: Either[String, Boolean] = Option(uri.getScheme) match {
        case Some("http")  => false.asRight
        case Some("https") => true.asRight
        case Some(other)   => s"Scheme $other is not supported, http and https only".asLeft
        case None          => false.asRight
      }
      val host     = Option(uri.getHost).toRight("Host must be present")
      val port     = Option(uri.getPort).filterNot(_ === -1)
      val fragment = Option(uri.getFragment).map(s => s"Fragment is not allowed in collector URI, $s given").toLeft(())
      val userinfo = Option(uri.getUserInfo).map(s => s"Userinfo is not allowed in collector URI, $s given").toLeft(())
      val path =
        Option(uri.getPath).filterNot(_.isEmpty).map(s => s"Path is not allowed in collector URI, $s given").toLeft(())
      val query     = Option(uri.getQuery).map(s => s"Query is not allowed in collector URI, $s given").toLeft(())
      val authority = Option(uri.getQuery).map(s => s"Authority is not allowed in collector URI, $s given").toLeft(())

      for {
        h <- host
        s <- https
        _ <- fragment
        _ <- userinfo
        _ <- path
        _ <- query
        _ <- authority
      } yield EndpointParams(h, port, s)
    }
  }

  /** ADT for possible track results */
  sealed trait Result extends Product with Serializable {

    def isSuccess: Boolean =
      this match {
        case Result.Success(_) => true
        case _                 => false
      }
  }

  object Result {

    /** Success. Collector accepted an event */
    final case class Success(code: Int) extends Result

    /** Collector refused an event. Probably wrong endpoint or outage */
    final case class Failure(code: Int) extends Result

    /** Other failure. Timeout or network unavailability */
    final case class TrackerFailure(throwable: Throwable) extends Result

    /** Emitter cannot continue retrying. Note that this is not *response* */
    final case class RetriesExceeded(lastResponse: Result) extends Result
  }

  /** Get delay with increased non-linear back-off period in millis */
  def getDelay(attempt: Int, seed: Double): Int = {
    val rangeMin = attempt.toDouble
    val rangeMax = attempt.toDouble * 3
    ((rangeMin + (rangeMax - rangeMin) * seed) * 1000).toInt
  }

}
