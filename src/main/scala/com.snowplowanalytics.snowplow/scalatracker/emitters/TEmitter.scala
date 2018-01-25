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
package emitters

import java.util.concurrent.BlockingQueue
import java.util.{Timer, TimerTask}

import scala.util.{Failure, Random, Success, Try}
import scala.concurrent.{ExecutionContext, Future}

import scalaj.http._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

/**
 * Emitters are entities in charge of transforming events sent from tracker
 * into actual HTTP requests (IO), which includes:
 * + Async/Multi-threading
 * + Queuing `EmitterPayload`
 * + Transforming `EmitterPayload` into Bytes
 * + Backup queue and callbacks
 */
trait TEmitter {
  import TEmitter._

  /**
   * Method called to send an event from the tracker to the emitter
   *
   * @param event Fully assembled event
   */
  def input(event: EmitterPayload): Unit
}

object TEmitter {

  /** ADT for possible track results */
  sealed trait CollectorResponse extends Product with Serializable
  /** Success. Collector accepted an event */
  case class CollectorSuccess(code: Int) extends CollectorResponse
  /** Collector refused an event. Probably wrong endpoint or outage */
  case class CollectorFailure(code: Int) extends CollectorResponse
  /** Other failure. Timeout or network unavailability */
  case class TrackerFailure(throwable: Throwable) extends CollectorResponse
  /** Emitter cannot continue retrying. Note that this is not *response* */
  case class RetriesExceeded(lastResponse: CollectorResponse) extends CollectorResponse

  /** User-provided callback */
  type Callback = (CollectorParams, CollectorRequest, CollectorResponse) => Unit

  /** Low-level representation of event */
  type EmitterPayload = Map[String, String]

  /** Payload (either GET or POST) ready to be send to collector */
  sealed trait CollectorRequest extends Product with Serializable {
    /** Attempt to send */
    def attempt: Int

    /** Check if emitters should keep sending this request */
    def isFailed: Boolean = attempt >= 10

    /** Increment attempt number. Must be used whenever payload failed */
    def updateAttempt: CollectorRequest = this match {
      case g: GetCollectorRequest => g.copy(attempt = attempt + 1)
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
  case class GetCollectorRequest(attempt: Int, payload: EmitterPayload) extends CollectorRequest
  /** Multiple events, supposed to passed with POST-request */
  case class PostCollectorRequest(attempt: Int, payload: List[EmitterPayload]) extends CollectorRequest

  /** Collector preferences */
  case class CollectorParams(host: String, port: Int, https: Boolean) {
    /** Return stringified collector representation, e.g. `https://splw.acme.com:80/` */
    def getUri: String = s"${if (https) "https" else "http"}://$host:$port"
  }

  object CollectorParams {
    /** Construct collector preferences with correct default port */
    def construct(host: String, port: Option[Int] = None, https: Boolean = false): CollectorParams =
      port match {
        case Some(p) => CollectorParams(host, p, https)
        case None if https => CollectorParams(host, 443, https = true)
        case None => CollectorParams(host, 80, https = false)
      }
  }

  // JSON object with Iglu URI to Schema for payload
  private val payloadBatchStub: JObject = ("schema", Tracker.PayloadDataSchemaKey.toSchemaUri)

  /**
    * Transform List of Map[String, String] to JSON array of objects
    *
    * @param payload list of string-to-string maps taken from HTTP query
    * @return JSON array represented as String
    */
  private def postPayload(payload: Seq[Map[String, String]]): String =
    compact(payloadBatchStub ~ ("data", payload))

  /**
    * Construct POST request with batch event payload
    *
    * @param collector endpoint preferences
    * @param request events enveloped with either Get or Post request
    * @return HTTP request with event
    */
  private[emitters] def constructRequest(collector: CollectorParams, request: CollectorRequest): HttpRequest = {
    request match {
      case PostCollectorRequest(_, payload) =>
        Http(s"${collector.getUri}/com.snowplowanalytics.snowplow/tp2")
          .postData(postPayload(payload))
          .header("content-type", "application/json")
      case GetCollectorRequest(_, payload) =>
        Http(s"${collector.getUri}/i").params(payload)
    }
  }

  /**
    * Attempt a HTTP request. Return request back to queue
    * if it was unsuccessful and invoke callback.
    * @param ec thread pool to send HTTP requests to collector
    * @param originQueue reference to queue, where event can be re-added
    *                    in case of unsuccessful delivery
    * @param collector endpoint preferences
    * @param payload either GET or POST payload
    */
  def submit(
    originQueue: BlockingQueue[CollectorRequest],
    ec: ExecutionContext,
    callback: Option[Callback],
    collector: CollectorParams,
    payload: CollectorRequest
  ): Unit = {
    val finish = invokeCallback(ec, collector, callback) _
    sendAsync(ec, collector, payload).onComplete { response =>
      val result = httpToCollector(response)
      finish(payload, result)
      result match {
        case CollectorSuccess(_) => ()
        case failure =>
          val updated = payload.updateAttempt
          val resend = backToQueue(originQueue, updated)
          if (!resend) finish(updated, RetriesExceeded(failure))
      }
    }(ec)
  }

  /**
    * Asynchronously execute user-provided callback against event and collector response
    * If callback fails to execute - message will be printed to stderr
    *
    * @param ec thread pool to asynchronously execute callback
    * @param collector collector parameters
    * @param callback user-provided callback
    * @param payload latest *sent* payload
    * @param result latest result
    */
  def invokeCallback(ec: ExecutionContext, collector: CollectorParams, callback: Option[Callback])(payload: CollectorRequest, result: CollectorResponse): Unit = {
    callback match {
      case Some(cb) =>
        Future(cb(collector, payload, result))(ec).onFailure { case throwable =>
          val error = Option(throwable.getMessage).getOrElse(throwable.toString)
          val message = s"Snowplow Tracker bounded to ${collector.getUri} failed to execute callback: $error"
          System.err.println(message)
        }(ec)
      case None => ()
    }
  }

  /**
    * Update sent-timestamp and attempt an HTTP request
    * @param ec thread pool to send HTTP requests to collector
    * @param collector endpoint preferences
    * @param payload either GET or POST payload
    */
  def sendAsync(ec: ExecutionContext, collector: CollectorParams, payload: CollectorRequest): Future[HttpResponse[_]] =
    Future(constructRequest(collector, payload.updateStm).asBytes)(ec)

  /** Transform implementation-specific response into tracker-specific */
  def httpToCollector(httpResponse: Try[HttpResponse[_]]): CollectorResponse = httpResponse match {
    case Success(s) if s.code >= 200 && s.code < 300 => CollectorSuccess(s.code)
    case Success(s) => CollectorFailure(s.code)
    case Failure(e) => TrackerFailure(e)
  }

  /** Timer thread, responsible for adding failed payloads to queue after delay */
  private val timer = new Timer("snowplow-event-retry-timer", true)

  /** RNG to generate back-off periods */
  private val rng = new Random()

  /**
    * Schedule re-adding of a failed event to queue after some delay.
    * Delay is calculated based on number of undertaken attempts
    */
  def backToQueue(queue: BlockingQueue[CollectorRequest], event: CollectorRequest): Boolean = {
    if (event.isFailed) false
    else {
      val task = new TimerTask {
        override def run(): Unit = queue.put(event)
      }
      val delay = getDelay(event.attempt)
      timer.schedule(task, delay)
      true
    }
  }

  /** Get delay with increased non-linear back-off period */
  private def getDelay(attempt: Int): Int = {
    val rangeMin = attempt.toDouble
    val rangeMax = attempt.toDouble * 3
    ((rangeMin + (rangeMax - rangeMin) * rng.nextDouble()) * 1000).toInt
  }
}
