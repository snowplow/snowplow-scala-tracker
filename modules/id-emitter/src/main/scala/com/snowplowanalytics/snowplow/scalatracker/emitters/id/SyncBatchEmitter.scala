/*
 * Copyright (c) 2018-2018 Snowplow Analytics Ltd. All rights reserved.
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

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

import cats.Id

import com.snowplowanalytics.snowplow.scalatracker.Emitter._

/**
 * Synchronous batch emitter
 * Store events in buffer and send them with POST request when buffer exceeds `bufferSize`.
 * The underlying buffer is thread-safe.
 * The actual sending of events blocks the current thread, up to @blockingDuration
 *
 * @param collector collector preferences
 * @param blockingDuration amount of time to wait (block) for response
 * @param bufferSize quantity of events in a batch request
 * @param callback optional callback executed after each sent event
 */
class SyncBatchEmitter(collector: CollectorParams,
                       blockingDuration: Duration,
                       bufferSize: Int,
                       callback: Option[Callback[Id]],
                       private val processor: RequestProcessor = new RequestProcessor)
    extends BaseEmitter {

  private val buffer = new ListBuffer[EmitterPayload]()

  override def send(event: EmitterPayload): Id[Unit] =
    buffer.synchronized {
      buffer.append(event)

      if (buffer.size >= bufferSize) {
        val payload = CollectorRequest.Post(1, buffer.toList)

        processor.sendSync(global, blockingDuration, collector, payload, callback)
        buffer.clear()
      }
    }

}

object SyncBatchEmitter {

  /**
   * Aux constructor for sync batch emitter
   *
   * @param host       collector host name
   * @param port       collector port number, default 80 for http and 443 for https
   * @param https      should this use the https scheme
   * @param bufferSize quantity of events in a batch request
   * @param callback   optional callback executed after each sent event
   * @param blockingDuration amount of time to wait (block) for response
   * @return emitter
   */
  def createAndStart(host: String,
                     port: Option[Int]              = None,
                     https: Boolean                 = false,
                     bufferSize: Int                = 50,
                     callback: Option[Callback[Id]] = None,
                     blockingDuration: Duration     = 5.seconds): SyncBatchEmitter = {
    val collector = CollectorParams(host, port, Some(https))
    new SyncBatchEmitter(collector, blockingDuration, bufferSize, callback)
  }
}
