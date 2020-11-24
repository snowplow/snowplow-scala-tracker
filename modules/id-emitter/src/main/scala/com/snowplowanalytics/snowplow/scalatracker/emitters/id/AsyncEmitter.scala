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

import scala.concurrent.ExecutionContext
import scala.collection.mutable.ListBuffer

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
   * @param host collector host
   * @param port collector port
   * @param https should this use the https scheme
   * @param bufferConfig Configures buffering of events, before they are sent to the collector in larger batches.
   * @param callback optional callback executed after each sent event, or failed attempt
   * @return emitter
   */
  def createAndStart(host: String,
                     port: Option[Int] = None,
                     https: Boolean    = false,
                     bufferConfig: BufferConfig,
                     callback: Option[Callback[Id]] = None)(implicit ec: ExecutionContext): AsyncEmitter = {
    val collector = EndpointParams(host, port, Some(https))
    val emitter   = new AsyncEmitter(ec, collector, bufferConfig, callback, RequestProcessor.defaultHttpClient)
    emitter.startWorker()
    emitter
  }
}

/**
 * Asynchronous emitter using LinkedBlockingQueue
 *
 * @param ec thread pool for async event sending
 * @param collector collector preferences
 * @param bufferConfig Configures buffering of events, before they are sent to the collector in larger batches.
 * @param callback optional callback executed after each sent event
 */
class AsyncEmitter private[id] (ec: ExecutionContext,
                                collector: EndpointParams,
                                bufferConfig: BufferConfig,
                                callback: Option[Callback[Id]],
                                client: RequestProcessor.HttpClient)
    extends BaseEmitter {

  private val buffer = ListBuffer[Payload]()

  /** Queue of HTTP requests */
  val queue = new LinkedBlockingQueue[Request]()

  val worker = new Thread {
    override def run(): Unit =
      while (true) {
        val event = queue.take()
        RequestProcessor.submit(queue, ec, callback, collector, event, client)
      }
  }

  worker.setDaemon(true)

  /**
   * Method called to send an event from the tracker to the emitter
   * Adds the event to the queue
   *
   * @param event Fully assembled event
   */
  def send(event: Payload): Unit =
    bufferConfig match {
      case BufferConfig.NoBuffering =>
        queue.put(Request(event))
      case BufferConfig.EventsCardinality(size) =>
        buffer.synchronized {
          buffer.append(event)
          if (buffer.size >= size) {
            queue.put(Request(buffer.toList))
            buffer.clear()
          }
        }
      case BufferConfig.PayloadSize(bytes) =>
        buffer.synchronized {
          buffer.append(event)
          if (payloadSize(buffer) >= bytes) {
            queue.put(Request(buffer.toList))
            buffer.clear()
          }
        }
    }

  private[id] def startWorker(): Unit =
    worker.start()
}
