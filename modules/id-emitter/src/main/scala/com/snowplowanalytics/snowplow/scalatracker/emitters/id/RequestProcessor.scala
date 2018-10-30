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
package com.snowplowanalytics.snowplow.scalatracker.emitters.id

import java.util.concurrent.BlockingQueue
import java.util.{Timer, TimerTask}

import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Random, Success, Try}

import cats.Id

import scalaj.http._

import com.snowplowanalytics.snowplow.scalatracker.Emitter._

class RequestProcessor {

  /** Timer thread, responsible for adding failed payloads to queue after delay */
  private val timer = new Timer("snowplow-event-retry-timer", true)

  /** RNG to generate back-off periods */
  private val rng = new Random()

  /**
   * Schedule re-adding of a failed event to queue after some delay.
   * Delay is calculated based on number of undertaken attempts
   */
  def backToQueue(queue: BlockingQueue[CollectorRequest], event: CollectorRequest): Boolean =
    if (event.isFailed) false
    else {
      val task = new TimerTask {
        override def run(): Unit = queue.put(event)
      }
      val delay = getDelay(event.attempt, rng.nextDouble())
      timer.schedule(task, delay.toLong)
      true
    }

  /**
   * Construct POST request with batch event payload
   *
   * @param collector endpoint preferences
   * @param request events enveloped with either Get or Post request
   * @return HTTP request with event
   */
  private[scalatracker] def constructRequest(collector: CollectorParams, request: CollectorRequest): HttpRequest =
    request match {
      case CollectorRequest.Post(_, payload) =>
        Http(collector.getPostUri)
          .postData(postPayload(payload))
          .header("content-type", "application/json")
      case CollectorRequest.Get(_, payload) =>
        Http(collector.getGetUri).params(payload)
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
    callback: Option[Callback[Id]],
    collector: CollectorParams,
    payload: CollectorRequest
  ): Unit = {
    val finish = invokeCallback(ec, collector, callback) _
    sendAsync(ec, collector, payload).onComplete { response =>
      val result = httpToCollector(response)
      finish(payload, result)
      result match {
        case Result.Success(_) => ()
        case failure =>
          val updated = payload.updateAttempt
          val resend  = backToQueue(originQueue, updated)
          if (!resend) finish(updated, Result.RetriesExceeded(failure))
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
  def invokeCallback(ec: ExecutionContext, collector: CollectorParams, callback: Option[Callback[Id]])(
    payload: CollectorRequest,
    result: Result): Unit =
    callback match {
      case Some(cb) =>
        Future(cb(collector, payload, result))(ec).failed.foreach { throwable =>
          val error   = Option(throwable.getMessage).getOrElse(throwable.toString)
          val message = s"Snowplow Tracker bounded to ${collector.getUri} failed to execute callback: $error"
          System.err.println(message)
        }(ec)
      case None => ()
    }

  /**
   * Update sent-timestamp and attempt an HTTP request
   * @param ec thread pool to send HTTP requests to collector
   * @param collector endpoint preferences
   * @param payload either GET or POST payload
   */
  def sendAsync(ec: ExecutionContext, collector: CollectorParams, payload: CollectorRequest): Future[HttpResponse[_]] =
    Future {
      val deviceSentTimestamp = System.currentTimeMillis()
      constructRequest(collector, payload.updateStm(deviceSentTimestamp)).asBytes
    }(ec)

  def sendSync(ec: ExecutionContext,
               duration: Duration,
               collector: CollectorParams,
               payload: CollectorRequest,
               callback: Option[Callback[Id]]): Unit = {
    val response = sendAsync(ec, collector, payload)
    val result =
      Await
        .ready(response, duration)
        .value
        .map(httpToCollector)
        .getOrElse(Result.TrackerFailure(new TimeoutException(s"Snowplow Sync Emitter timed out after $duration")))

    callback match {
      case None     => ()
      case Some(cb) => cb(collector, payload, result)
    }
  }

  /** Transform implementation-specific response into tracker-specific */
  def httpToCollector(httpResponse: Try[HttpResponse[_]]): Result = httpResponse match {
    case Success(s) if s.code >= 200 && s.code < 300 => Result.Success(s.code)
    case Success(s)                                  => Result.Failure(s.code)
    case Failure(e)                                  => Result.TrackerFailure(e)
  }
}
