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

import TEmitter.{CollectorParams, GetCollectorRequest, sendAsync}

/**
 * Blocking emitter.
 * This emitter blocks whole thread (from global execution context)
 * for specified amount of time. Use at own risk
 * @param host collector host
 * @param port collector port
 * @param https whether to use HTTPS
 * @param blockingDuration amount of time to wait (block) for response
 */
class SyncEmitter(host: String, port: Int = 80, https: Boolean = false, blockingDuration: Duration = 5.seconds, val callback: Option[TEmitter.Callback]) extends TEmitter {

  val collectorParams = CollectorParams(host, port, https)

  def input(event: Map[String, String]): Unit = {
    val payload = GetCollectorRequest(1, event)
    val response = sendAsync(global, collectorParams, payload)
    val result =
      Await.ready(response, blockingDuration)
        .value
        .map(TEmitter.httpToCollector)
        .getOrElse(TEmitter.TrackerFailure(new TimeoutException(s"Snowplow Sync Emitter timed out after $blockingDuration")))

    callback match {
      case None => ()
      case Some(cb) => cb(collectorParams, payload, result)
    }
  }
}
