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
package com.snowplowanalytics.snowplow.scalatracker.emitters.http4s

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import cats.implicits._
import cats.effect.{ContextShift, IO, Resource, Timer}

import fs2.concurrent.Queue

import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, MediaType, Request}
import org.http4s.headers.`Content-Type`
import org.http4s.server.jetty.JettyBuilder

import com.snowplowanalytics.snowplow.scalatracker.Emitter.CollectorParams

object Collector {

  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  implicit val timer: Timer[IO]     = IO.timer(global)

  val Host   = "localhost"
  val Port   = 8080
  val Params = CollectorParams(Host, Port, false)
  val Pixel  = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg=="

  def routes(queue: Queue[IO, Request[IO]]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "i" =>
      val response = Ok(Pixel.getBytes).map(_.withContentType(`Content-Type`(MediaType.image.png)))
      queue.enqueue1(req) *> response
    case req @ POST -> Root / "com.snowplowanalytics.snowplow/tp2" =>
      val response = Ok().map(_.withContentType(`Content-Type`(MediaType.image.png)))
      queue.enqueue1(req) *> response
  }

  def getServerLog: Resource[IO, Queue[IO, Request[IO]]] =
    Resource
      .liftF(Queue.unbounded[IO, Request[IO]].map(q => (q, routes(q))))
      .flatMap {
        case (queue, routes) =>
          JettyBuilder[IO]
            .bindHttp(8080, "localhost")
            .mountService(routes, "/")
            .withShutdownTimeout(1.second)
            .resource
            .as(queue)
      }
}
