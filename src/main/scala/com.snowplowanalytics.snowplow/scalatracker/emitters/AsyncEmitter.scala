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

import scala.concurrent.ExecutionContext

import TEmitter._

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
   * @return emitter
   */
  def createAndStart(host: String, port: Option[Int] = None, https: Boolean = false, callback: Option[Callback])(implicit ec: ExecutionContext): AsyncEmitter = {
    val collector = CollectorParams.construct(host, port, https)
    val emitter = new AsyncEmitter(ec, collector, callback)
    emitter.startWorker()
    emitter
  }
}

/**
 * Asynchronous emitter using LinkedBlockingQueue
 *
 * @param ec thread pool for async event sending
 * @param collector collector preferences
 * @param callback optional callback executed after each sent event
 */
class AsyncEmitter private(ec: ExecutionContext, collector: CollectorParams, callback: Option[Callback]) extends TEmitter {

  /** Queue of HTTP requests */
  val queue = new LinkedBlockingQueue[CollectorRequest]()

  val worker = new Thread {
    override def run() {
      while (true) {
        val event = queue.take()
        submit(queue, ec, callback, collector, event)
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
    queue.put(GetCollectorRequest(1, event))
  }

  private def startWorker(): Unit = {
    worker.start()
  }
}

