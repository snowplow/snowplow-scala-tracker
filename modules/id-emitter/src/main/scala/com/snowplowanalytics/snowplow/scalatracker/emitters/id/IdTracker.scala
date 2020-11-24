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

import cats.Id
import cats.data.NonEmptyList
import cats.effect.Clock
import io.circe.Json
import java.util.UUID

import com.snowplowanalytics.iglu.core.SelfDescribingData
import com.snowplowanalytics.snowplow.scalatracker.{Emitter, Subject, Tracker => BaseTracker, UUIDProvider}

object IdTracker {

  private[id] implicit val idClock: Clock[Id] = new Clock[Id] {
    import concurrent.duration._

    override def realTime(unit: TimeUnit): Id[Long] = unit.convert(System.currentTimeMillis(), MILLISECONDS)

    override def monotonic(unit: TimeUnit): Id[Long] = unit.convert(System.nanoTime(), NANOSECONDS)
  }

  private implicit val uuidProvider: UUIDProvider[Id] = new UUIDProvider[Id] {

    override def generateUUID: Id[UUID] = UUID.randomUUID()
  }

  /** Create a Snowplow Tracker for an id-emitter using standard Clock and UUIDProvider instances
   *
   * @param emitters Sequence of emitters to which events are passed
   * @param namespace Tracker namespace
   * @param appId ID of the application
   * @param subject Subject to attach to every snowplow event
   * @param encodeBase64 Whether to encode JSONs
   * @param metadata optionally a json containing the metadata context for the running instance
   */
  def apply(emitters: NonEmptyList[Emitter[Id]],
            namespace: String,
            appId: String,
            subject: Subject                           = Subject(),
            encodeBase64: Boolean                      = true,
            metadata: Option[SelfDescribingData[Json]] = None): BaseTracker[Id] =
    BaseTracker(emitters, namespace, appId, subject, encodeBase64, metadata)

  /** Create a Snowplow Tracker for an id-emitter using standard Clock and UUIDProvider instances
   *
   * @param emitter Emitter to which events are passed
   * @param namespace Tracker namespace
   * @param appId ID of the application
   */
  def apply(emitter: Emitter[Id], namespace: String, appId: String): BaseTracker[Id] =
    BaseTracker(emitter, namespace, appId)

}
