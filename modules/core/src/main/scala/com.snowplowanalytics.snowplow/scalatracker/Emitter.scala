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

import java.net.URI

import cats.syntax.either._

import io.circe._
import io.circe.syntax._

import com.snowplowanalytics.iglu.core.SelfDescribingData
import com.snowplowanalytics.iglu.core.circe.implicits._

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

  /** User-provided callback */
  type Callback[F[_]] = (CollectorParams, CollectorRequest, Result) => F[Unit]

  /** Emitter buffering config */
  sealed trait BufferConfig extends Product with Serializable

  object BufferConfig {
    case object Get extends BufferConfig
    case class Post(size: Int) extends BufferConfig
    case class Sized(bytes: Int) extends BufferConfig
  }

  /** Payload (either GET or POST) ready to be send to collector */
  sealed trait CollectorRequest extends Product with Serializable {

    /** Attempt to send */
    def attempt: Int

    /** Check if emitters should keep sending this request */
    def isFailed: Boolean = attempt >= 10

    /** Increment attempt number. Must be used whenever payload failed */
    def updateAttempt: CollectorRequest = this match {
      case g: CollectorRequest.Get  => g.copy(attempt = attempt + 1)
      case p: CollectorRequest.Post => p.copy(attempt = attempt + 1)
    }

    /**
     * Return same payload, but with updated stm
     * Must be used right before payload goes to collector
     */
    def updateStm(deviceSentTimestamp: Long): CollectorRequest = {
      val stm = deviceSentTimestamp.toString
      this match {
        case CollectorRequest.Get(_, map) =>
          CollectorRequest.Get(attempt, map.updated("stm", stm))
        case CollectorRequest.Post(_, list) =>
          CollectorRequest.Post(attempt, list.map(_.updated("stm", stm)))
      }
    }
  }

  object CollectorRequest {

    /** Single event, supposed to passed with GET-request */
    final case class Get(attempt: Int, payload: EmitterPayload) extends CollectorRequest

    /** Multiple events, supposed to passed with POST-request */
    final case class Post(attempt: Int, payload: List[EmitterPayload]) extends CollectorRequest

    /** Initial POST */
    private[scalatracker] def apply(payload: List[EmitterPayload]): CollectorRequest =
      Post(1, payload)

    /** Initial GET */
    private[scalatracker] def apply(payload: EmitterPayload): CollectorRequest =
      Get(1, payload)
  }

  /** Collector preferences */
  case class CollectorParams(host: String, port: Int, https: Boolean) {

    /** Return stringified collector representation, e.g. `https://splw.acme.com:80/` */
    def getUri: String = s"${if (https) "https" else "http"}://$host:$port"

    def getPostUri: String = getUri ++ "/com.snowplowanalytics.snowplow/tp2"

    def getGetUri: String = getUri ++ "/i"
  }

  object CollectorParams {

    /** Construct collector preferences with correct default port */
    def apply(host: String, port: Option[Int], https: Option[Boolean]): CollectorParams =
      (port, https) match {
        case (Some(p), s)    => CollectorParams(host, p, s.getOrElse(false))
        case (None, Some(s)) => CollectorParams(host, if (s) 443 else 80, s)
        case (None, None)    => CollectorParams(host, 80, false)
      }

    /** Construct collector preferences from URI, fail on any unexpected element */
    def fromUri(uri: URI): Either[String, CollectorParams] = {
      val https = Option(uri.getScheme) match {
        case Some("http")  => Some(false).asRight
        case Some("https") => Some(true).asRight
        case Some(other)   => s"Scheme $other is not supported, http and https only".asLeft
        case None          => None.asRight
      }
      val host = Option(uri.getHost).toRight("Host must be present")
      val port = if (uri.getPort == -1) None else Some(uri.getPort)
      val fragment = Option(uri.getFragment).fold(().asRight[String])(s =>
        s"Fragment is not allowed in collector URI, $s given".asLeft[Unit])
      val userinfo = Option(uri.getUserInfo).fold(().asRight[String])(s =>
        s"Userinfo is not allowed in collector URI, $s given".asLeft[Unit])
      val path = Option(uri.getPath)
        .flatMap(p => if (p.isEmpty) None else Some(p))
        .fold(().asRight[String])(s => s"Path is not allowed in collector URI, $s given".asLeft[Unit])
      val query = Option(uri.getQuery).fold(().asRight[String])(s =>
        s"Query is not allowed in collector URI, $s given".asLeft[Unit])
      val authority = Option(uri.getQuery).fold(().asRight[String])(s =>
        s"Authority is not allowed in collector URI, $s given".asLeft[Unit])

      for {
        h <- host
        s <- https
        _ <- fragment
        _ <- userinfo
        _ <- path
        _ <- query
        _ <- authority
      } yield CollectorParams(h, port, s)
    }
  }

  /** ADT for possible track results */
  sealed trait Result extends Product with Serializable

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

  /**
   * Transform List of Map[String, String] to JSON array of objects
   *
   * @param payload list of string-to-string maps taken from HTTP query
   * @return JSON array represented as String
   */
  private[scalatracker] def postPayload(payload: Seq[Map[String, String]]): String =
    SelfDescribingData[Json](Tracker.PayloadDataSchemaKey, payload.asJson).normalize.noSpaces

}
