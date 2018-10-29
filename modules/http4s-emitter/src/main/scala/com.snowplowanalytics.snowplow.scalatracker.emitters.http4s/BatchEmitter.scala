package com.snowplowanalytics.snowplow.scalatracker
package emitters.http4s

import cats.effect.{ContextShift, Fiber, IO, Resource}

import fs2.{Sink, Stream}
import fs2.concurrent.Queue

import org.http4s.client._
import org.http4s.{Request, Response}

import Emitter._

case class BatchEmitter(q: Queue[IO, EmitterPayload], fiber: Fiber[IO, Unit]) extends Emitter[IO] {
  def send(event: EmitterPayload): IO[Unit] =
    q.enqueue1(event)
}

object BatchEmitter {

  def httpToCollector(httpResponse: Response[IO]): CollectorResponse =
    if (httpResponse.status.code == 200) CollectorSuccess(200)
    else CollectorFailure(httpResponse.status.code)

  def processResponse(response: Response[IO]) =
    IO.pure(httpToCollector(response))

  def send(callback: Option[Callback[IO]], client: Client[IO]): Sink[IO, EmitterPayload] =
    (s: Stream[IO, EmitterPayload]) =>
      s.evalMap[IO, Unit] { payload =>
        val request = Request[IO]()
        val result  = client.fetch(request)(processResponse).attempt.map(_.fold(TrackerFailure.apply, identity))
        for { r <- result } yield r
        ???
    }

  def init(collector: CollectorParams, callback: Option[Callback[IO]], client: Client[IO])(
    implicit S: ContextShift[IO]): Resource[IO, BatchEmitter] = {
    val init = for {
      queue <- Queue.bounded[IO, EmitterPayload](10)
      sink = send(callback, client)
      fiber <- queue.dequeue.observe(sink).compile.drain.start
    } yield BatchEmitter(queue, fiber)
    Resource.make(init)(_.fiber.cancel)
  }
}
