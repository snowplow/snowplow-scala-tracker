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
package com.snowplowanalytics.snowplow.scalatracker.emitters.id

import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.{Failure, Random, Success, Try}
import scala.util.control.NonFatal

import cats.Id

import scalaj.http._

import com.snowplowanalytics.snowplow.scalatracker.Emitter._
import com.snowplowanalytics.snowplow.scalatracker.Payload

private[id] object RequestProcessor {

  type HttpClient = HttpRequest => HttpResponse[Array[Byte]]
  lazy val defaultHttpClient: HttpClient = _.asBytes

  private lazy val rng = new Random()

  /**
    * Construct POST request with batch event payload
    *
    * @param collector endpoint preferences
    * @param request events enveloped with either Get or Post request
    * @param options Options to configure the Http transaction.
    * @return HTTP request with event
    */
  def constructRequest(collector: EndpointParams, request: Request, options: Seq[HttpOptions.HttpOption]): HttpRequest =
    request match {
      case Request.Buffered(_, payload) =>
        Http(collector.getPostUri)
          .postData(Payload.postPayload(payload))
          .header("content-type", "application/json")
          .options(options)
      case Request.Single(_, payload) =>
        Http(collector.getGetUri).params(payload.toMap).options(options)
    }

  lazy val logger = LoggerFactory.getLogger(getClass.getName)

  /**
    * Executes user-provided callback against event and collector response
    * If callback fails to execute - message will be printed to stderr
    *
    * @param collector collector parameters
    * @param callback user-provided callback
    * @param payload latest *sent* payload
    * @param result latest result
    */
  def invokeCallback(
    collector: EndpointParams,
    callback: Option[Callback[Id]]
  )(payload: Request, result: Result): Unit =
    callback.foreach { cb =>
      try {
        cb(collector, payload, result)
      } catch {
        case NonFatal(throwable) =>
          if (logger.isWarnEnabled)
            logger.warn(s"Snowplow Tracker bounded to ${collector.getUri} failed to execute callback", throwable)
      }
    }

  /** Sends http requests using scalaj's thread-blocking api
    *
    *  Uses a scala.concurrent.blocking construct, to allow the global execution pool
    *  to create an extra thread of necessary.
    *
    */
  def sendSync(
    collector: EndpointParams,
    request: Request,
    callback: Option[Callback[Id]],
    options: Seq[HttpOptions.HttpOption],
    client: HttpClient
  ): Result = {

    val deviceSentTimestamp = System.currentTimeMillis()
    val httpRequest         = constructRequest(collector, request.updateStm(deviceSentTimestamp), options)
    val tried               = Try { blocking { client(httpRequest) } }
    val result              = httpToCollector(tried)

    invokeCallback(collector, callback)(request, result)
    result
  }

  /** Sends http requests using scalaj's thread-blocking api, until they succeed */
  def sendSyncAndRetry(
    collector: EndpointParams,
    request: Request,
    callback: Option[Callback[Id]],
    retryPolicy: RetryPolicy,
    options: Seq[HttpOptions.HttpOption],
    client: HttpClient
  ): Unit = {
    def go(request: Request): Unit = {
      val result = sendSync(collector, request, callback, options, client)
      if (!result.isSuccess && !request.isFailed(retryPolicy)) {
        blocking {
          Thread.sleep(RetryPolicy.getDelay(request.attempt, rng.nextDouble()))
        }
        go(request.updateAttempt)
      }
    }
    go(request)
  }

  /** Sends http requests using scalaj's thread-blocking api until they succeed.
    *
    *  Http calls are wrapped in Futures, using flatmapping to enable competitive yielding to other tasks on the threadpool.
    */
  def sendAsync(
    collector: EndpointParams,
    request: Request,
    callback: Option[Callback[Id]],
    retryPolicy: RetryPolicy,
    options: Seq[HttpOptions.HttpOption],
    client: HttpClient
  )(implicit ec: ExecutionContext): Future[Unit] =
    Future {
      sendSync(collector, request, callback, options, client)
    }.flatMap { result =>
      if (!result.isSuccess && !request.isFailed(retryPolicy)) {
        blocking {
          Thread.sleep(RetryPolicy.getDelay(request.attempt, rng.nextDouble()))
        }
        sendAsync(collector, request.updateAttempt, callback, retryPolicy, options, client)
      } else
        Future.unit
    }

  /** Transform implementation-specific response into tracker-specific */
  def httpToCollector(httpResponse: Try[HttpResponse[_]]): Result = httpResponse match {
    case Success(s) if s.code >= 200 && s.code < 300 => Result.Success(s.code)
    case Success(s)                                  => Result.Failure(s.code)
    case Failure(e)                                  => Result.TrackerFailure(e)
  }

}
