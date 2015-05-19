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

import java.util.concurrent.LinkedBlockingQueue

object AsyncEmitter {

  // Avoid starting thread in constructor
  def createAndStart(host: String, port: Int = 80): AsyncEmitter = {
    val emitter = new AsyncEmitter(host, port)
    emitter.startWorker()
    emitter
  }

}

class AsyncEmitter private(host: String, port: Int) extends TEmitter {

  val queue = new LinkedBlockingQueue[Map[String, String]]()

  // 10 second timeout between failed requests
  val BackoffPeriod = 10000

  val worker = new Thread {
    override def run {
      while (true) {
        val event = queue.take()

        while (!RequestUtils.attemptGet(host, event, port)) {
          Thread.sleep(BackoffPeriod)
        }
      }
    }
  }

  worker.setDaemon(true)

  def input(event: Map[String, String]) {
    queue.put(event)
  }

  private def startWorker() {
    worker.start()
  }
}
