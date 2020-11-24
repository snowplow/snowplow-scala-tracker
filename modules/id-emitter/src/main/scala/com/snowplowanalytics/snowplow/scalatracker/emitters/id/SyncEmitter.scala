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

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer

import cats.Id

import com.snowplowanalytics.snowplow.scalatracker.Emitter._
import com.snowplowanalytics.snowplow.scalatracker.Payload

/**
 * Blocking emitter.
 * This emitter blocks whole thread (from global execution context)
 * for specified amount of time. Use at own risk
 * @param collector collector preferences
 * @param blockingDuration amount of time to wait (block) for response
 * @param callback optional callback executed after each sent event
 * @param ec The execution context on which to block
 *
 */
class SyncEmitter private[id] (collector: EndpointParams,
                               blockingDuration: Duration,
                               bufferConfig: BufferConfig,
                               callback: Option[Callback[Id]],
                               ec: ExecutionContext,
                               client: RequestProcessor.HttpClient)
    extends BaseEmitter {

  private val buffer = new ListBuffer[Payload]()

  def send(event: Payload): Unit =
    bufferConfig match {
      case BufferConfig.NoBuffering =>
        val payload = Request(event)
        RequestProcessor.sendSync(ec, blockingDuration, collector, payload, callback, client)
      case BufferConfig.EventsCardinality(size) =>
        buffer.synchronized {
          buffer.append(event)
          if (buffer.size >= size) {
            val payload = Request(buffer.toList)
            RequestProcessor.sendSync(ec, blockingDuration, collector, payload, callback, client)
            buffer.clear()
          }
        }
      case BufferConfig.PayloadSize(bytes) =>
        buffer.synchronized {
          buffer.append(event)
          if (payloadSize(buffer) >= bytes) {
            val payload = Request(buffer.toList)
            RequestProcessor.sendSync(ec, blockingDuration, collector, payload, callback, client)
            buffer.clear()
          }
        }
    }
}

object SyncEmitter {

  /**
   * Create the sync emitter
   *
   * @param collector The [[EndpointParams]] for the snowplow collector
   * @param bufferConfig Configures buffering of events, before they are sent to the collector in larger batches.
   * @param callback optional callback executed after each sent event
   * @param blockingDuration amount of time to wait (block) for response
   * @param executionContext The execution context on which to make (blocking) http requests
   * @return emitter
   */
  def apply(collector: EndpointParams,
            bufferConfig: BufferConfig,
            callback: Option[Callback[Id]]     = None,
            blockingDuration: Duration         = 5.seconds,
            executionContext: ExecutionContext = ExecutionContext.global): SyncEmitter =
    new SyncEmitter(collector,
                    blockingDuration,
                    bufferConfig,
                    callback,
                    executionContext,
                    RequestProcessor.defaultHttpClient)

}
