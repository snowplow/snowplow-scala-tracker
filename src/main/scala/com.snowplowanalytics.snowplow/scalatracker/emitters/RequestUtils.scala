/*
 * Copyright (c) 2015-2017 Snowplow Analytics Ltd. All rights reserved.
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

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Success, Random }

import java.util.concurrent.BlockingQueue
import java.util.{Timer, TimerTask}

import scalaj.http._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

/**
 * Module responsible for communication with collector
 */
object RequestUtils {

  /** Payload (either GET or POST) ready to be send to collector */
  sealed trait CollectorRequest extends Product with Serializable {
    /** Attempt to send */
    def attempt: Int

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

  case class GetCollectorRequest(attempt: Int, payload: Map[String, String]) extends CollectorRequest
  case class PostCollectorRequest(attempt: Int, payload: List[Map[String, String]]) extends CollectorRequest


  // JSON object with Iglu URI to Schema for payload
  private val payloadBatchStub: JObject = ("schema", "iglu:com.snowplowanalytics.snowplow/payload_data/jsonschema/1-0-4")

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
   * @param host URL host (not header)
   * @param request events enveloped with either Get or Post request
   * @param port URL port (not header)
   * @param https should this request use the https scheme
   * @return HTTP request with event
   */
  private[emitters] def constructRequest(host: String, request: CollectorRequest, port: Int, https: Boolean = false): HttpRequest = {
    val scheme = if (https) "https://" else "http://"
    request match {
      case PostCollectorRequest(_, payload) =>
        Http(s"$scheme$host:$port/com.snowplowanalytics.snowplow/tp2")
          .postData(postPayload(payload))
          .header("content-type", "application/json")
      case GetCollectorRequest(_, payload) =>
        val scheme = if (https) "https://" else "http://"
        Http(s"$scheme$host:$port/i").params(payload)
    }
  }

  /**
   * Attempt a HTTP request. Return request back to queue
   * if it was unsuccessful
   * @param originQueue reference to queue, where event can be re-added
   *                    in case of unsuccessful delivery
   * @param host collector host
   * @param port collector port
   * @param https should this request use the https scheme
   * @param payload either GET or POST payload
   */
  def send(originQueue: BlockingQueue[CollectorRequest], host: String, port: Int = 80, https: Boolean, payload: CollectorRequest): Unit = {
    sendAsync(host, port, https = https, payload).onComplete {
      case Success(s) if s.code >= 200 && s.code < 300 => ()
      case _ => backToQueue(originQueue, payload.updateAttempt)
    }
  }

  /**
    * Attempt a HTTP request
    * @param host collector host
    * @param port collector port
    * @param https should this request use the https scheme
    * @param payload either GET or POST payload
    */
  def sendAsync(host: String, port: Int = 80, https: Boolean, payload: CollectorRequest): Future[HttpResponse[_]] =
    Future(constructRequest(host, payload.updateStm, port, https).asBytes)

  /** Timer thread, responsible for adding failed payloads to queue after delay */
  private val timer = new Timer("snowplow-event-retry-timer", true)

  /** RNG to generate back-off periods */
  private val rng = new Random()

  /**
    * Schedule re-adding of a failed event to queue after some delay.
    * Delay is calculated based on number of undertaken attempts
    */
  def backToQueue(queue: BlockingQueue[CollectorRequest], event: CollectorRequest): Unit = {
    if (event.attempt > 10) System.err.println("Snowplow Scala Tracker gave up trying to send a payload to collector after 10 attempts")
    else {
      val task = new TimerTask {
        override def run(): Unit = queue.put(event)
      }
      val delay = getDelay(event.attempt)
      timer.schedule(task, delay)
    }
  }

  /** Get delay with increased non-linear back-off period */
  private def getDelay(attempt: Int): Int = {
    val rangeMin = attempt.toDouble
    val rangeMax = attempt.toDouble * 3
    ((rangeMin + (rangeMax - rangeMin) * rng.nextDouble()) * 1000).toInt
  }
}
