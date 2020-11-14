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

import scalaj.http.HttpOptions

import cats.Id
import cats.implicits._

import com.snowplowanalytics.snowplow.scalatracker.Emitter._
import com.snowplowanalytics.snowplow.scalatracker.Payload

/**
 * Blocking emitter.
 * This emitter blocks the whole thread. Use at own risk
 * @param collector collector preferences
 * @param callback optional callback executed after each sent event
 * @param retryPolicy Configures how the emiiter retries sending events to the collector in case of failure.
 * @param client executes http requests
 * @param httpOptions Options to configure the Http transaction. The default sets readTimeout and
 * connTimeout to 5 seconds to guard against blocking the thread for too long.
 *
 */
class SyncEmitter private[id] (collector: EndpointParams,
                               bufferConfig: BufferConfig,
                               callback: Option[Callback[Id]],
                               retryPolicy: RetryPolicy,
                               client: RequestProcessor.HttpClient,
                               httpOptions: Seq[HttpOptions.HttpOption])
    extends BaseEmitter {

  import SyncEmitter._

  private var state = State(Nil, 0, 0)

  override def send(event: Payload): Unit = {
    val toSend = state.synchronized {
      state = state.add(event)
      if (state.isFull(bufferConfig)) {
        val payloads = state.payloads
        state = State(Nil, 0, 0)
        payloads
      } else {
        Nil
      }
    }
    sendEvents(toSend)
  }

  def flush(): Unit = {
    val toSend = state.synchronized {
      val payloads = state.payloads
      state = State(Nil, 0, 0)
      payloads
    }
    sendEvents(toSend)
  }

  private def sendEvents(events: List[Payload]): Unit =
    events match {
      case Nil => ()
      case single :: Nil if bufferConfig == BufferConfig.NoBuffering =>
        RequestProcessor.sendSyncAndRetry(collector, Request(single), callback, retryPolicy, httpOptions, client)
      case more =>
        RequestProcessor.sendSyncAndRetry(collector, Request(more), callback, retryPolicy, httpOptions, client)
    }
}

object SyncEmitter {

  /**
   * Aux constructor for sync emitter
   *
   * @param host collector host name
   * @param port collector port number, default 80 for http and 443 for https
   * @param https should this use the https scheme
   * @param bufferConfig Configures buffering of events, before they are sent to the collector in larger batches.
   * @param callback optional callback executed after each sent event
   * @param retryPolicy Configures how the emiiter retries sending events to the collector in case of failure.
   * @param options Options to configure the Http transaction. The default sets readTimeout and
   * connTimeout to 5 seconds to guard against blocking the thread for too long.
   * @return emitter
   */
  def createAndStart(host: String,
                     port: Option[Int] = None,
                     https: Boolean    = false,
                     bufferConfig: BufferConfig,
                     callback: Option[Callback[Id]]       = None,
                     retryPolicy: RetryPolicy             = RetryPolicy.Default,
                     options: Seq[HttpOptions.HttpOption] = defaultHttpOptions): SyncEmitter = {
    val collector = EndpointParams(host, port, Some(https))
    new SyncEmitter(collector, bufferConfig, callback, retryPolicy, RequestProcessor.defaultHttpClient, options)
  }

  private val defaultHttpOptions = Seq(HttpOptions.readTimeout(5000), HttpOptions.connTimeout(5000))

  private case class State(payloads: List[Payload], items: Int, bytes: Int) {

    def add(payload: Payload): State = {
      val nextBytes = if (items === 0) Payload.sizeOf(Seq(payload)) else bytes + Payload.sizeContributionOf(payload)
      State(payload :: payloads, items + 1, nextBytes)
    }

    def isFull(bufferConfig: BufferConfig): Boolean =
      bufferConfig match {
        case BufferConfig.NoBuffering            => true
        case BufferConfig.EventsCardinality(max) => items >= max
        case BufferConfig.PayloadSize(max)       => bytes >= max
      }
  }

}
