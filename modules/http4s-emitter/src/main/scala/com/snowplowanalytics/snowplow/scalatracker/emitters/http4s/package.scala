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
package com.snowplowanalytics.snowplow.scalatracker.emitters

import cats.effect.{Clock, Sync}
import com.snowplowanalytics.snowplow.scalatracker.Tracking

import java.util.UUID

package object http4s {

  implicit def ceTracking[F[_]: Clock: Sync]: Tracking[F] = new Tracking[F] {
    import scala.concurrent.duration.MILLISECONDS

    override def getCurrentTimeMillis: F[Long] = Clock[F].realTime(MILLISECONDS)

    override def generateUUID: F[UUID] = Sync[F].delay(UUID.randomUUID)

  }

}
