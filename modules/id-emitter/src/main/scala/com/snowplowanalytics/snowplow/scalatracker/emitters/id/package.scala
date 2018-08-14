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
package com.snowplowanalytics.snowplow.scalatracker.emitters

import java.util.UUID

import cats.Id
import com.snowplowanalytics.snowplow.scalatracker.{ClockProvider, UUIDProvider}

package object id {

  implicit val clockProvider: ClockProvider[Id] = new ClockProvider[Id] {

    override def getCurrentMilliseconds: Id[Long] = System.currentTimeMillis()
  }

  implicit val uuidProvider: UUIDProvider[Id] = new UUIDProvider[Id] {

    override def generateUUID: Id[UUID] = UUID.randomUUID()
  }
}
