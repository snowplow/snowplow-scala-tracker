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

import java.util.concurrent.LinkedBlockingQueue

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext

import TEmitter._

object AsyncBatchEmitter {
  // Avoid starting thread in constructor
  /**
   * Start async emitter with batch event payload
   *
   * @param host collector host name
   * @param port collector port number, default 80 for http and 443 for https
   * @param bufferSize quantity of events in batch request
   * @param https should this use the https scheme
   * @param callback optional callback executed after each sent event
   * @param ec thread pool to send HTTP requests to collector
   * @return emitter
   */
  def createAndStart(host: String, port: Option[Int] = None, https: Boolean = false, bufferSize: Int = 50, callback: Option[Callback] = None)(implicit ec: ExecutionContext): AsyncBatchEmitter = {
    val collector = CollectorParams.construct(host, port, https)
    val emitter = new AsyncBatchEmitter(ec, collector, bufferSize, callback)
    emitter.startWorker()
    emitter
  }
}

/**
 * Asynchronous batch emitter
 * Store events in buffer and send them with POST request when buffer exceeds `bufferSize`
 * Backed by `java.util.concurrent.LinkedBlockingQueue`, which has
 * capacity of `Int.MaxValue` will block thread when buffer reach capacity
 *
 * @param ec thread pool for async event sending
 * @param collector collector preferences
 * @param bufferSize quantity of events in a batch request
 */
class AsyncBatchEmitter private(ec: ExecutionContext, collector: CollectorParams, bufferSize: Int, callback: Option[Callback]) extends TEmitter {

  private var buffer = ListBuffer[Map[String, String]]()

  /** Queue of HTTP requests */
  val queue = new LinkedBlockingQueue[CollectorRequest]()

  // Start consumer thread synchronously trying to send events to collector
  val worker = new Thread {
    override def run() {
      while (true) {
        val batch = queue.take()
        submit(queue, ec, callback, collector, batch)
      }
    }
  }

  worker.setDaemon(true)

  /**
   * Method called to send an event from the tracker to the emitter
   * Adds the event to the queue
   *
   * @param event Fully assembled event
   */
  def input(event: EmitterPayload): Unit = {
    // Multiple threads can input via same tracker and override buffer
    buffer.synchronized {
      buffer.append(event)
      if (buffer.size >= bufferSize) {
        queue.put(PostCollectorRequest(1, buffer.toList))
        buffer = ListBuffer[Map[String, String]]()
      }
    }
  }

  private def startWorker(): Unit = {
    worker.start()
  }
}
