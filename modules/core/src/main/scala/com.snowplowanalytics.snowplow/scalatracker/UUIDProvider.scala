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

import cats.effect.Sync
import java.util.UUID

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

object UUIDProvider {

  /** Instance summoner */
  def apply[F[_]](implicit ev: UUIDProvider[F]): UUIDProvider[F] = ev

  /** Construct provider from referentially transparent action */
  def instance[F[_]](generator: F[UUID]): UUIDProvider[F] =
    new UUIDProvider[F] {
      val generateUUID = generator
    }

  implicit def syncInstance[F[_]: Sync]: UUIDProvider[F] =
    instance(Sync[F].delay(UUID.randomUUID))

}
