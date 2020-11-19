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

import cats.effect.{ContextShift, IO, Resource, Timer}
import cats.implicits._
import java.util.concurrent.atomic.AtomicInteger
import org.http4s.Response
import org.http4s.client.Client
import scala.concurrent.ExecutionContext

import com.snowplowanalytics.snowplow.scalatracker.{Emitter, Payload}

import org.specs2.Specification

class Http4sEmitterSpec extends Specification {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val timer: Timer[IO]     = IO.timer(ExecutionContext.global)

  override def is = s2"""

    Http4sEmitter's buffer should not flush before reaching buffer's event cardinality limit $e1
    Http4sEmitter's buffer should flush after reaching buffer's event cardinality limit      $e2
    Http4sEmitter's buffer should not flush before reaching buffer's payload size limit      $e3
    Http4sEmitter's buffer should flush after reaching buffer's payload size limit           $e4
    Http4sEmitter's buffer should flush unsent events after closing                          $e5

  """

  val payload = Payload(Map("foo" -> "bar", "bar" -> "foo"))

  def e1 = {
    val counter = new AtomicInteger(0)
    val client = Client[IO] { request =>
      counter.getAndIncrement
      Resource.pure[IO, Response[IO]](Response[IO]())
    }

    val collector    = Emitter.EndpointParams("example.com")
    val bufferConfig = Emitter.BufferConfig.EventsCardinality(3)

    Http4sEmitter
      .build[IO](collector, client, bufferConfig)
      .use { emitter =>
        List
          .fill(2)(emitter.send(payload))
          .sequence
          .map { _ =>
            Thread.sleep(100)
            counter.get must_== 0
          }
      }
      .unsafeRunSync()
  }

  def e2 = {
    val counter = new AtomicInteger(0)
    val client = Client[IO] { request =>
      counter.getAndIncrement
      Resource.pure[IO, Response[IO]](Response[IO]())
    }

    val collector    = Emitter.EndpointParams("example.com")
    val bufferConfig = Emitter.BufferConfig.EventsCardinality(3)

    Http4sEmitter
      .build[IO](collector, client, bufferConfig)
      .use { emitter =>
        List
          .fill(3)(emitter.send(payload))
          .sequence
          .map { _ =>
            eventually(counter.get must_== 1)
          }
      }
      .unsafeRunSync()
  }

  def e3 = {
    val counter = new AtomicInteger(0)
    val client = Client[IO] { request =>
      counter.getAndIncrement
      Resource.pure[IO, Response[IO]](Response[IO]())
    }

    val maxBytes     = Payload.postPayload(Seq(payload, payload, payload)).getBytes.length
    val collector    = Emitter.EndpointParams("example.com")
    val bufferConfig = Emitter.BufferConfig.PayloadSize(maxBytes)

    Http4sEmitter
      .build[IO](collector, client, bufferConfig)
      .use { emitter =>
        List
          .fill(2)(emitter.send(payload))
          .sequence
          .map { _ =>
            Thread.sleep(100)
            counter.get must_== 0
          }
      }
      .unsafeRunSync()
  }

  def e4 = {
    val counter = new AtomicInteger(0)
    val client = Client[IO] { request =>
      counter.getAndIncrement
      Resource.pure[IO, Response[IO]](Response[IO]())
    }

    val maxBytes     = Payload.postPayload(Seq(payload, payload, payload)).getBytes.length
    val collector    = Emitter.EndpointParams("example.com")
    val bufferConfig = Emitter.BufferConfig.PayloadSize(maxBytes)

    Http4sEmitter
      .build[IO](collector, client, bufferConfig)
      .use { emitter =>
        List
          .fill(3)(emitter.send(payload))
          .sequence
          .map { _ =>
            eventually(counter.get must_== 1)
          }
      }
      .unsafeRunSync()

  }

  def e5 = {
    val counter = new AtomicInteger(0)
    val client = Client[IO] { request =>
      counter.getAndIncrement
      Resource.pure[IO, Response[IO]](Response[IO]())
    }

    val maxBytes     = Payload.postPayload(Seq(payload, payload, payload)).getBytes.length
    val collector    = Emitter.EndpointParams("example.com")
    val bufferConfig = Emitter.BufferConfig.PayloadSize(maxBytes)

    Http4sEmitter
      .build[IO](collector, client, bufferConfig)
      .use { emitter =>
        emitter.send(payload)
      }
      .unsafeRunSync()
    eventually(counter.get must_== 1)
  }

}
