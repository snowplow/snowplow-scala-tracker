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
   * @param port collector port number
   * @param bufferSize quantity of events in batch request
   * @param https should this use the https scheme
   * @param ec thread pool to send HTTP requests to collector
   * @return emitter
   */
  def createAndStart(host: String, port: Int = 80, bufferSize: Int = 50, https: Boolean = false, callback: Option[Callback] = None)(implicit ec: ExecutionContext): AsyncBatchEmitter = {
    val emitter = new AsyncBatchEmitter(ec, host, port, bufferSize, https = https, callback = callback)
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
 * @param host collector host name
 * @param port collector port number
 * @param bufferSize quantity of events in a batch request
 * @param https should this use the https scheme
 */
class AsyncBatchEmitter private(ec: ExecutionContext, host: String, port: Int, bufferSize: Int, https: Boolean = false, callback: Option[Callback] = None) extends TEmitter {

  private var buffer = ListBuffer[Map[String, String]]()

  /** Queue of HTTP requests */
  val queue = new LinkedBlockingQueue[CollectorRequest]()

  val collectorParams = CollectorParams(host, port, https)

  // Start consumer thread synchronously trying to send events to collector
  val worker = new Thread {
    override def run() {
      while (true) {
        val batch = queue.take()
        submit(queue, ec, callback, collectorParams, batch)
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
