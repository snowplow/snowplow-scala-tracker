/*
 * Copyright (c) 2015 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.snowplow.scalatracker.emitters

// Java
import java.util.concurrent.LinkedBlockingQueue


object AsyncEmitter {
  // Avoid starting thread in constructor
  /**
   * Start async emitter with single event payload
   *
   * @param host collector host
   * @param port collector port
   * @return emitter
   */
  def createAndStart(host: String, port: Int = 80): AsyncEmitter = {
    val emitter = new AsyncEmitter(host, port)
    emitter.startWorker()
    emitter
  }
}

/**
 * Asynchronous emitter using LinkedBlockingQueue
 *
 * @param host collector host
 * @param port collector port
 */
class AsyncEmitter private(host: String, port: Int) extends TEmitter {

  val queue = new LinkedBlockingQueue[Map[String, String]]()

  // 2 seconds timeout after 1st failed request
  val initialBackoffPeriod = 2000

  // TODO: consider move retryGet/PostUntilSuccessful with adding of stm to Emitter (it's not requests logic)
  val worker = new Thread {
    override def run {
      while (true) {
        val event = queue.take()
        RequestUtils.retryGetUntilSuccessful(host, event, port, initialBackoffPeriod)
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
  def input(event: Map[String, String]): Unit = {
    queue.put(event)
  }

  private def startWorker(): Unit = {
    worker.start()
  }
}

