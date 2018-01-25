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
package com.snowplowanalytics.snowplow.scalatracker
package emitters

import java.util.concurrent.TimeoutException

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

import TEmitter._

/**
 * Blocking emitter.
 * This emitter blocks whole thread (from global execution context)
 * for specified amount of time. Use at own risk
 * @param collector collector preferences
 * @param blockingDuration amount of time to wait (block) for response
 * @param callback optional callback executed after each sent event
 *
 */
class SyncEmitter(collector: CollectorParams, blockingDuration: Duration, callback: Option[Callback]) extends TEmitter {

  def input(event: Map[String, String]): Unit = {
    val payload = GetCollectorRequest(1, event)
    val response = sendAsync(global, collector, payload)
    val result =
      Await.ready(response, blockingDuration)
        .value
        .map(httpToCollector)
        .getOrElse(TrackerFailure(new TimeoutException(s"Snowplow Sync Emitter timed out after $blockingDuration")))

    callback match {
      case None => ()
      case Some(cb) => cb(collector, payload, result)
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
  def createAndStart(host: String, port: Option[Int] = None, https: Boolean = false, callback: Option[Callback] = None, blockingDuration: Duration = 5.seconds): SyncEmitter = {
    val collector = CollectorParams.construct(host, port, https)
    new SyncEmitter(collector, blockingDuration, callback)
  }
}
