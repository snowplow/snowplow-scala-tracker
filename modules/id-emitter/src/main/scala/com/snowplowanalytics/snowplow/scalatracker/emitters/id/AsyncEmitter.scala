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

import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.{ExecutionContext, Future, blocking}
import scalaj.http.HttpOptions

import cats.Id

import com.snowplowanalytics.snowplow.scalatracker.Emitter._
import com.snowplowanalytics.snowplow.scalatracker.{Buffer, Payload}

object AsyncEmitter {
  // Avoid starting thread in constructor
  /**
   * Start async emitter with single event payload
   * Backed by `java.util.concurrent.LinkedBlockingQueue`, which has
   * capacity of `Int.MaxValue` will block thread when buffer reach capacity
   *
   * This emitter sends requests asynchronously from the tracker's meain thread of execution,
   * but in doing so it blocks a thread on the provided execution context for
   * each http request.
   *
   * The blocking calls are wrapped in scala's blocking construct (https://www.scala-lang.org/api/2.12.4/scala/concurrent/index.html#blocking)
   * which is respected by the global execution context.
   *
   * @param host collector host
   * @param port collector port
   * @param https should this use the https scheme
   * @param bufferConfig Configures buffering of events, before they are sent to the collector in larger batches.
   * @param callback optional callback executed after each sent event, or failed attempt
   * @param retryPolicy Configures how the emiiter retries sending events to the collector in case of failure.
   * @param queuePolicy Configures how the emitter's `send` method behaves when the queue is full.
   * @param httpOptions Options to configure the http transaction
   * @return emitter
   */
  def createAndStart(host: String,
                     port: Option[Int]                        = None,
                     https: Boolean                           = false,
                     bufferConfig: BufferConfig               = BufferConfig.Default,
                     callback: Option[Callback[Id]]           = None,
                     retryPolicy: RetryPolicy                 = RetryPolicy.Default,
                     queuePolicy: EventQueuePolicy            = EventQueuePolicy.Default,
                     httpOptions: Seq[HttpOptions.HttpOption] = Nil)(implicit ec: ExecutionContext): AsyncEmitter = {
    val collector = EndpointParams(host, port, Some(https))
    val emitter = new AsyncEmitter(collector,
                                   bufferConfig,
                                   callback,
                                   retryPolicy,
                                   queuePolicy,
                                   RequestProcessor.defaultHttpClient,
                                   httpOptions,
                                   1000)
    emitter.startWorker()
    emitter
  }

}

/**
 * Asynchronous emitter using LinkedBlockingQueue
 *
 * @param collector collector preferences
 * @param bufferConfig Configures buffering of events, before they are sent to the collector in larger batches.
 * @param callback optional callback executed after each sent event
 * @param retryPolicy Configures how the emiiter retries sending events to the collector in case of failure.
 * @param client executes http requests
 * @param httpOptions Options to configure the http transaction
 */
class AsyncEmitter private[id] (collector: EndpointParams,
                                bufferConfig: BufferConfig,
                                callback: Option[Callback[Id]],
                                retryPolicy: RetryPolicy,
                                queuePolicy: EventQueuePolicy,
                                client: RequestProcessor.HttpClient,
                                httpOptions: Seq[HttpOptions.HttpOption],
                                pollTimeoutMillis: Long)
    extends BaseEmitter {

  private val queue = queuePolicy.tryLimit match {
    case None        => new LinkedBlockingQueue[Payload]()
    case Some(limit) => new LinkedBlockingQueue[Payload](limit)
  }

  private val isClosing        = new AtomicBoolean(false)
  private val suspendBuffering = new AtomicBoolean(false)

  private[id] def startWorker()(implicit ec: ExecutionContext): Unit = {
    def nextTick(): Future[Unit] =
      if (!isClosing.get || Option(queue.peek).nonEmpty) {
        workerTick().flatMap(_ => nextTick())
      } else Future.unit
    nextTick()
  }

  private def pollQueue(implicit ec: ExecutionContext): Future[Option[Payload]] =
    Future {
      blocking {
        Option(queue.poll(pollTimeoutMillis, TimeUnit.MILLISECONDS))
      }
    }

  private def workerTick()(implicit ec: ExecutionContext): Future[Unit] = {

    def fillBuffer(buffer: Buffer): Future[Buffer] =
      pollQueue.flatMap {
        case Some(event) =>
          val next = buffer.add(event)
          if (!next.isFull) fillBuffer(next) else Future.successful(next)
        case None =>
          if (!suspendBuffering.getAndSet(false)) {
            fillBuffer(buffer)
          } else {
            Future.successful(buffer)
          }
      }

    fillBuffer(Buffer(bufferConfig))
      .map(_.toRequest)
      .flatMap {
        case Some(request) =>
          RequestProcessor.sendAsync(collector, request, callback, retryPolicy, httpOptions, client)
        case None =>
          Future.unit
      }
  }

  override def send(payload: Payload): Unit =
    queuePolicy match {
      case EventQueuePolicy.UnboundedQueue | EventQueuePolicy.BlockWhenFull(_) =>
        queue.put(payload)
      case EventQueuePolicy.IgnoreWhenFull(_) =>
        queue.offer(payload)
      case EventQueuePolicy.ErrorWhenFull(limit) =>
        if (!queue.offer(payload))
          throw new EventQueuePolicy.EventQueueException(limit)
    }

  def flush(): Unit =
    suspendBuffering.set(true)

  def close(): Unit = {
    suspendBuffering.set(true)
    isClosing.set(true)
  }

}
