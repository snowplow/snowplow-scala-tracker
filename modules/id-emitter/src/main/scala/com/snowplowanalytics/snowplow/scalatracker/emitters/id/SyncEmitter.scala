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
package com.snowplowanalytics.snowplow.scalatracker.emitters.id

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer

import cats.Id

import com.snowplowanalytics.snowplow.scalatracker.Emitter._

/**
 * Blocking emitter.
 * This emitter blocks whole thread (from global execution context)
 * for specified amount of time. Use at own risk
 * @param collector collector preferences
 * @param blockingDuration amount of time to wait (block) for response
 * @param callback optional callback executed after each sent event
 *
 */
class SyncEmitter(collector: EndpointParams,
                  blockingDuration: Duration,
                  bufferConfig: BufferConfig,
                  callback: Option[Callback[Id]],
                  private val processor: RequestProcessor = new RequestProcessor)
    extends BaseEmitter {

  private val buffer = new ListBuffer[Payload]()

  def send(event: Payload): Unit =
    bufferConfig match {
      case BufferConfig.NoBuffering =>
        val payload = Request.Single(1, event)
        processor.sendSync(global, blockingDuration, collector, payload, callback)
      case BufferConfig.EventsCardinality(size) =>
        buffer.synchronized {
          buffer.append(event)
          if (buffer.size >= size) {
            val payload = Request.Buffered(1, buffer.toList)
            processor.sendSync(global, blockingDuration, collector, payload, callback)
            buffer.clear()
          }
        }
      case BufferConfig.PayloadSize(bytes) =>
        buffer.synchronized {
          buffer.append(event)
          if (bufferSizeInBytes(buffer) >= bytes) {
            val payload = Request.Buffered(1, buffer.toList)
            processor.sendSync(global, blockingDuration, collector, payload, callback)
            buffer.clear()
          }
        }
    }
}

object SyncEmitter {

  /**
   * Aux constructor for sync emitter
   *
   * @param host collector host name
   * @param port collector port number, default 80 for http and 443 for https
   * @param https should this use the https scheme
   * @param callback optional callback executed after each sent event
   * @return emitter
   */
  def createAndStart(host: String,
                     bufferConfig: BufferConfig,
                     port: Option[Int]              = None,
                     https: Boolean                 = false,
                     callback: Option[Callback[Id]] = None,
                     blockingDuration: Duration     = 5.seconds): SyncEmitter = {
    val collector = EndpointParams(host, port, Some(https))
    new SyncEmitter(collector, blockingDuration, bufferConfig, callback)
  }
}
