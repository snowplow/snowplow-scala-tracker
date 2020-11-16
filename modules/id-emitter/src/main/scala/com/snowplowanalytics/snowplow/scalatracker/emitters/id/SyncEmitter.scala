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

import com.snowplowanalytics.snowplow.scalatracker.Emitter._
import com.snowplowanalytics.snowplow.scalatracker.{Buffer, Payload}

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

  private var buffer = Buffer(bufferConfig)

  override def send(event: Payload): Unit = {
    val toSend = buffer.synchronized {
      buffer = buffer.add(event)
      if (buffer.isFull) {
        val request = buffer.toRequest
        buffer = Buffer(bufferConfig)
        request
      } else {
        None
      }
    }

    toSend.foreach { request =>
      RequestProcessor.sendSyncAndRetry(collector, request, callback, retryPolicy, httpOptions, client)
    }
  }

  def flush(): Unit = {
    val toSend = buffer.synchronized {
      val request = buffer.toRequest
      buffer = Buffer(bufferConfig)
      request
    }
    toSend.foreach { request =>
      RequestProcessor.sendSyncAndRetry(collector, request, callback, retryPolicy, httpOptions, client)
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
   * @param bufferConfig Configures buffering of events, before they are sent to the collector in larger batches.
   * @param callback optional callback executed after each sent event
   * @param retryPolicy Configures how the emiiter retries sending events to the collector in case of failure.
   * @param options Options to configure the Http transaction. The default sets readTimeout and
   * connTimeout to 5 seconds to guard against blocking the thread for too long.
   * @return emitter
   */
  def createAndStart(host: String,
                     port: Option[Int]                    = None,
                     https: Boolean                       = false,
                     bufferConfig: BufferConfig           = BufferConfig.Default,
                     callback: Option[Callback[Id]]       = None,
                     retryPolicy: RetryPolicy             = RetryPolicy.Default,
                     options: Seq[HttpOptions.HttpOption] = defaultHttpOptions): SyncEmitter = {
    val collector = EndpointParams(host, port, Some(https))
    new SyncEmitter(collector, bufferConfig, callback, retryPolicy, RequestProcessor.defaultHttpClient, options)
  }

  private val defaultHttpOptions = Seq(HttpOptions.readTimeout(5), HttpOptions.connTimeout(5))

}
