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

import java.util.UUID

/**
 * This trait is needed here because `Tracker` is referentially transparent, and we leave it up for the emitters
 * to decide whether `F` will be referentially transparent also.
 */
trait ClockProvider[F[_]] {

  /**
   * Returns the current time in milliseconds. Most likely it will be `System.currentTimeMillis()` wrapped in `F`
   * @return current time in milliseconds
   */
  def getCurrentMilliseconds: F[Long]

}

/**
 * This trait is needed here because `Tracker` is referentially transparent, and we leave it up for the emitters
 * to decide whether `F` will be referentially transparent also.
 */
trait UUIDProvider[F[_]] {

  /**
   * Returns a random UUID. Most likely it will be `UUID.randomUUID()` wrapped in `F`
   * @return current time in milliseconds
   */
  def generateUUID: F[UUID]

}

/**
 * Emitters are entities in charge of transforming events sent from tracker
 * into actual HTTP requests (IO), which includes:
 * + Async/Multi-threading
 * + Queuing `EmitterPayload`
 * + Transforming `EmitterPayload` into Bytes
 * + Backup queue and callbacks
 *
 */
trait Emitter[F[_]] {
  import Emitter._

  /**
   * Emits the event payload
   *
   * @param event Fully assembled event
   */
  def send(event: EmitterPayload): F[Unit]

}

object Emitter {

  /** Low-level representation of event */
  type EmitterPayload = Map[String, String]

}
