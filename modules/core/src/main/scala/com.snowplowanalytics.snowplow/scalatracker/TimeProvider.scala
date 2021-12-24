/*
 * Copyright (c) 2013-2020 Snowplow Analytics Ltd. All rights reserved.
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

/**
  * This trait is needed here because `Tracker` is referentially transparent, and we leave it up for the emitters
  * to decide whether `F` will be referentially transparent also.
  */
trait TimeProvider[F[_]] {

  /**
    * Returns the current time expressed in milliseconds. Most likely it will be `System.currentTimeMillis()` wrapped in `F`
    * @return current time in milliseconds
    */
  def getCurrentTimeMillis: F[Long]

}

object TimeProvider {

  /** Instance summoner */
  def apply[F[_]](implicit ev: TimeProvider[F]): TimeProvider[F] = ev

}
