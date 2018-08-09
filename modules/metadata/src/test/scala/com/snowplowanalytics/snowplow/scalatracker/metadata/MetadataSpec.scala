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
package com.snowplowanalytics.snowplow.scalatracker.metadata

import java.net.{SocketTimeoutException, UnknownHostException}
import java.util.UUID

import cats.Id
import cats.data.NonEmptyList
import cats.effect.IO

import com.snowplowanalytics.snowplow.scalatracker.{Emitter, Tracker}
import com.snowplowanalytics.snowplow.scalatracker.Emitter.EmitterPayload

import org.specs2.Specification

class MetadataSpec extends Specification {
  def is = s2"""

    ec2 extension method should make a request and throw an exception $e1
    gce extension method should make a request and throw an exception $e2

    """

  val emitter: Emitter[Id] = new Emitter[Id] {
    override def send(event: EmitterPayload): Id[Unit] = ()

    override def getCurrentMilliseconds: Id[Long] = System.currentTimeMillis()

    override def generateUUID: Id[UUID] = UUID.randomUUID()
  }

  def e1 =
    Tracker(NonEmptyList.of(emitter), "foo", "foo")
      .enableEc2Context[IO]
      .unsafeRunSync() must throwA[SocketTimeoutException]

  def e2 =
    Tracker(NonEmptyList.of(emitter), "foo", "foo")
      .enableGceContext[IO]
      .unsafeRunSync() must throwA[UnknownHostException]

}
