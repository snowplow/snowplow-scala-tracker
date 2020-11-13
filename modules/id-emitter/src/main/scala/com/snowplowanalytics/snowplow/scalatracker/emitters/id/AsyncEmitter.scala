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

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, blocking}
import scalaj.http.HttpOptions

import cats.Id

import com.snowplowanalytics.snowplow.scalatracker.Emitter._
import com.snowplowanalytics.snowplow.scalatracker.Payload

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
   * @param httpOptions Options to configure the http transaction
   * @return emitter
   */
  def createAndStart(collector: EndpointParams,
                     port: Option[Int] = None,
                     https: Boolean    = false,
                     bufferConfig: BufferConfig,
                     callback: Option[Callback[Id]]           = None,
                     httpOptions: Seq[HttpOptions.HttpOption] = Nil)(implicit ec: ExecutionContext): AsyncEmitter = {
    val emitter =
      new AsyncEmitter(collector, bufferConfig, callback, RequestProcessor.defaultHttpClient, httpOptions, 1000)
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
 * @param client executes http requests
 * @param httpOptions Options to configure the http transaction
 */
class AsyncEmitter private[id] (collector: EndpointParams,
                                bufferConfig: BufferConfig,
                                callback: Option[Callback[Id]],
                                client: RequestProcessor.HttpClient,
                                httpOptions: Seq[HttpOptions.HttpOption],
                                pollTimeoutMillis: Long)
    extends BaseEmitter {

  private val buffer       = new LinkedBlockingQueue[Payload]()
  private val eventsToSend = new LinkedBlockingQueue[Payload]()

  private val isClosing        = new AtomicBoolean(false)
  private val suspendBuffering = new AtomicBoolean(false)

  private[id] def startWorker()(implicit ec: ExecutionContext): Unit = {
    def nextTick(): Future[Unit] =
      if (!isClosing.get || Option(buffer.peek).nonEmpty) {
        workerTick().flatMap(_ => nextTick())
      } else Future.unit
    nextTick()
  }

  private def pollBuffer(implicit ec: ExecutionContext): Future[Option[Payload]] =
    Future {
      blocking {
        Option(buffer.poll(pollTimeoutMillis, TimeUnit.MILLISECONDS))
      }
    }

  private def isBufferFull(count: Int, bytes: Int): Boolean =
    bufferConfig match {
      case BufferConfig.NoBuffering => true
      case BufferConfig.EventsCardinality(max) =>
        count >= max && count > 0
      case BufferConfig.PayloadSize(max) =>
        bytes >= max && bytes > 0
    }

  private def workerTick()(implicit ec: ExecutionContext): Future[Unit] = {

    def go(count: Int, bytes: Int): Future[Unit] =
      if (!isBufferFull(count, bytes)) {
        pollBuffer.flatMap {
          case Some(event) =>
            eventsToSend.offer(event)
            if (count == 0)
              go(1, Payload.sizeOf(Seq(event)))
            else
              go(count + 1, bytes + Payload.sizeContributionOf(event))
          case None =>
            if (!suspendBuffering.getAndSet(false)) {
              go(count, bytes)
            } else Future.unit
        }
      } else Future.unit

    go(0, 0).flatMap { _ =>
      drainEventsToSend() match {
        case Nil => Future.unit
        case single :: Nil if bufferConfig == BufferConfig.NoBuffering =>
          RequestProcessor.sendAsync(collector, Request(single), callback, httpOptions, client)
        case more =>
          RequestProcessor.sendAsync(collector, Request(more), callback, httpOptions, client)
      }
    }
  }

  private def drainEventsToSend(): List[Payload] = {
    val buf = new java.util.ArrayList[Payload]()
    eventsToSend.drainTo(buf)
    buf.asScala.toList
  }

  override def send(payload: Payload): Unit =
    buffer.put(payload)

  def flush(): Unit =
    suspendBuffering.set(true)

  def close(): Unit = {
    suspendBuffering.set(true)
    isClosing.set(true)
  }

}
