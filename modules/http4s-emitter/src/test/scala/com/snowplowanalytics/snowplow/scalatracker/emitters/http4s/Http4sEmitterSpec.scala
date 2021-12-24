/*
 * Copyright (c) 2020-2020 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.snowplow.scalatracker.emitters.http4s

import cats.effect.{IO, Ref, Resource}
import cats.effect.std.Random
import cats.effect.unsafe.implicits.global
import cats.implicits._

import org.http4s.Response
import org.http4s.client.Client
import com.snowplowanalytics.snowplow.scalatracker.{Emitter, Payload}
import org.specs2.Specification

import scala.concurrent.duration._

class Http4sEmitterSpec extends Specification {

  override def is = s2"""

    Http4sEmitter's buffer should not flush before reaching buffer's event cardinality limit $e1
    Http4sEmitter's buffer should flush after reaching buffer's event cardinality limit      $e2
    Http4sEmitter's buffer should not flush before reaching buffer's payload size limit      $e3
    Http4sEmitter's buffer should flush after reaching buffer's payload size limit           $e4
    Http4sEmitter's buffer should flush unsent events after closing                          $e5

  """

  val payload = Payload(Map("foo" -> "bar", "bar" -> "foo"))
  val threeEventsPayloadBytes = Payload.postPayload(Seq(payload, payload, payload)).getBytes.length
  val maxEventsBufferConfig = Emitter.BufferConfig.EventsCardinality(3)
  val maxBytesBufferConfig = Emitter.BufferConfig.PayloadSize(threeEventsPayloadBytes)

  def e1 = testEmitter(maxEventsBufferConfig, 2).unsafeRunSync() must_==((0, 1): (Int, Int))

  def e2 = testEmitter(maxEventsBufferConfig, 3).unsafeRunSync() must_==((1, 1): (Int, Int))

  def e3 = testEmitter(maxBytesBufferConfig, 2).unsafeRunSync() must_==((0, 1): (Int, Int))

  def e4 = testEmitter(maxBytesBufferConfig, 3).unsafeRunSync() must_==((1, 1): (Int, Int))

  def e5 = testEmitter(maxBytesBufferConfig, 1).unsafeRunSync() must_==((0, 1): (Int, Int))

  def testEmitter(bufferConfig: Emitter.BufferConfig, events: Int): IO[(Int, Int)] =
    for {
      rng <- Random.scalaUtilRandom[IO]
      ref <- Ref.of[IO, Int](0)
      collector = Emitter.EndpointParams("example.com")
      client = Client[IO] { _ => Resource.eval(ref.update(_ + 1)).as(Response[IO]()) }
      emitter = Http4sEmitter.build[IO](collector, client, bufferConfig)(implicitly, rng)
      beforeClose <- emitter.use { e => List.fill(events)(e.send(payload)).sequence_ >> IO.sleep(900.millis) >> ref.get }
      afterClose <- ref.get
    } yield (beforeClose, afterClose)

}
