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

import java.util.concurrent.LinkedBlockingQueue

import scala.concurrent.{ExecutionContext, Future, blocking}
import scalaj.http.HttpOptions

import cats.Id

import com.snowplowanalytics.snowplow.scalatracker.Emitter._
import com.snowplowanalytics.snowplow.scalatracker.{Buffer, Payload}
import com.snowplowanalytics.snowplow.scalatracker.Buffer.Action

object AsyncEmitter {
  // Avoid starting thread in constructor
  /**
   * Start async emitter with single event payload
   * Backed by `java.util.concurrent.LinkedBlockingQueue`, which has
   * capacity of `Int.MaxValue` will block thread when buffer reach capacity
   *
   * This emitter sends requests asynchronously from the tracker's main thread of execution,
   * but in doing so it blocks a thread on the provided execution context for
   * each http request.
   *
   * The blocking calls are wrapped in scala's blocking construct (https://www.scala-lang.org/api/2.12.4/scala/concurrent/index.html#blocking)
   * which is respected by the global execution context.
   *
   * @param collector The [[EndpointParams]] for the snowplow collector
   * @param bufferConfig Configures buffering of events, before they are sent to the collector in larger batches.
   * @param callback optional callback executed after each sent event, or failed attempt
   * @param retryPolicy Configures how the emiiter retries sending events to the collector in case of failure.
   * @param queuePolicy Configures how the emitter's `send` method behaves when the queue is full.
   * @param httpOptions Options to configure the http transaction
   * @return emitter
   */
  def createAndStart(collector: EndpointParams,
                     bufferConfig: BufferConfig               = BufferConfig.Default,
                     callback: Option[Callback[Id]]           = None,
                     retryPolicy: RetryPolicy                 = RetryPolicy.Default,
                     queuePolicy: EventQueuePolicy            = EventQueuePolicy.Default,
                     httpOptions: Seq[HttpOptions.HttpOption] = Nil)(implicit ec: ExecutionContext): AsyncEmitter = {
    val emitter = new AsyncEmitter(collector,
                                   bufferConfig,
                                   callback,
                                   retryPolicy,
                                   queuePolicy,
                                   RequestProcessor.defaultHttpClient,
                                   httpOptions)
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
                                httpOptions: Seq[HttpOptions.HttpOption])(implicit ec: ExecutionContext)
    extends BaseEmitter
    with java.lang.AutoCloseable {

  private val queue = queuePolicy.tryLimit match {
    case None        => new LinkedBlockingQueue[Action]()
    case Some(limit) => new LinkedBlockingQueue[Action](limit)
  }

  private[id] def startWorker(): Unit = {
    def nextTick(): Future[WorkerStatus] =
      workerTick().flatMap {
        case Active       => nextTick()
        case ShuttingDown => Future.successful(ShuttingDown)
      }
    nextTick()
  }

  private def takeFromQueue(): Future[Action] =
    Future {
      blocking {
        queue.take()
      }
    }

  private sealed trait WorkerStatus
  private object ShuttingDown extends WorkerStatus
  private object Active extends WorkerStatus

  private def workerTick(): Future[WorkerStatus] = {

    def fillBuffer(buffer: Buffer): Future[(Option[Request], WorkerStatus)] =
      takeFromQueue().flatMap {
        case Action.Flush =>
          Future.successful(buffer.toRequest -> Active)
        case Action.Terminate =>
          Future.successful(buffer.toRequest -> ShuttingDown)
        case Action.Enqueue(payload) =>
          val next = buffer.add(payload)
          if (next.isFull) Future.successful(next.toRequest -> Active)
          else fillBuffer(next)
      }

    fillBuffer(Buffer(bufferConfig))
      .flatMap {
        case (Some(request), status) =>
          RequestProcessor
            .sendAsync(collector, request, callback, retryPolicy, httpOptions, client)
            .map(_ => status)
        case (None, status) =>
          Future.successful(status)
      }
  }

  override def send(payload: Payload): Unit =
    queuePolicy match {
      case EventQueuePolicy.UnboundedQueue | EventQueuePolicy.BlockWhenFull(_) =>
        queue.put(Action.Enqueue(payload))
      case EventQueuePolicy.IgnoreWhenFull(_) =>
        queue.offer(Action.Enqueue(payload))
      case EventQueuePolicy.ErrorWhenFull(limit) =>
        if (!queue.offer(Action.Enqueue(payload)))
          throw new EventQueuePolicy.EventQueueException(limit)
    }

  override def flushBuffer(): Unit =
    Future {
      blocking {
        queue.put(Action.Flush)
      }
    }

  override def close(): Unit =
    Future {
      blocking {
        queue.put(Action.Terminate)
      }
    }

}
