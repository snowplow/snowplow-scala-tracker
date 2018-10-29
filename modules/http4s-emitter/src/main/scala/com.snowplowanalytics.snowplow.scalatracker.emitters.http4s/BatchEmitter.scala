package com.snowplowanalytics.snowplow.scalatracker
package emitters.http4s

import cats.effect.{ContextShift, Fiber, IO, Resource}
import cats.effect.concurrent.Ref
import cats.syntax.functor._

import fs2.{Sink, Stream}
import fs2.concurrent.Queue

import org.http4s.{Method, Request, Response, Uri}
import org.http4s.client._

import Emitter._

trait BatchEmitter extends Emitter[IO] {
  def send(event: EmitterPayload): IO[Unit]

  private[scalatracker] def fiber: Fiber[IO, Unit]
}

object BatchEmitter {

  def httpToCollector(httpResponse: Response[IO]): Result =
    if (httpResponse.status.code == 200) Result.Success(200)
    else Result.Failure(httpResponse.status.code)

  def processResponse(response: Response[IO]) =
    IO.pure(httpToCollector(response))

  def toRequest(collectorParams: CollectorParams, req: CollectorRequest): Request[IO] =
    req match {
      case CollectorRequest.Post(_, payload) =>
        val body = Stream.emits(Emitter.postPayload(payload).getBytes).covary[IO]
        Request[IO](Method.POST, Uri.unsafeFromString(collectorParams.getGetUri), body = body)
      case CollectorRequest.Get(_, payload) =>
        val uri = Uri.unsafeFromString(collectorParams.getGetUri).setQueryParams(payload.mapValues(v => List(v)))
        Request[IO](Method.GET, uri)
    }

  def send(collector: CollectorParams,
           callback: Option[Callback[IO]],
           client: Client[IO],
           queue: Queue[IO, CollectorRequest]): Sink[IO, CollectorRequest] =
    (s: Stream[IO, CollectorRequest]) =>
      s.evalMap[IO, Unit] { payload =>
        val request = toRequest(collector, payload)
        val finish: Result => IO[Unit] =
          callback.fold((_: Result) => IO.unit)(cb => r => cb(collector, payload, r))
        for {
          result <- client.fetch(request)(processResponse).attempt.map(_.fold(Result.TrackerFailure.apply, identity))
          _      <- finish(result)
          _ <- result match {
            case Result.Success(_) => IO.unit
            case failure =>
              for {
                resend <- backToQueue(queue, payload)
                _      <- if (resend) finish(Result.RetriesExceeded(failure)) else IO.unit
              } yield ()
          }
        } yield ()
    }

  def backToQueue(queue: Queue[IO, CollectorRequest], event: CollectorRequest): IO[Boolean] =
    if (event.isFailed) IO.pure(false)
    else queue.enqueue1(event).as(true)

  def addItem(bufferConfig: BufferConfig)(queue: Queue[IO, CollectorRequest], buffer: Ref[IO, Vector[EmitterPayload]])(
    event: EmitterPayload) =
    bufferConfig match {
      case BufferConfig.Post(allowed) =>
        for {
          currentSize <- buffer.get.map(_.length)
          _ <- if (currentSize >= allowed)
            buffer.getAndSet(Vector.empty).flatMap(payload => queue.enqueue1(CollectorRequest(payload.toList)))
          else buffer.update(_ :+ event)
        } yield ()
      case BufferConfig.Get =>
        queue.enqueue1(CollectorRequest(event))
    }

  def init(client: Client[IO], collector: CollectorParams, bufferConfig: BufferConfig, callback: Option[Callback[IO]])(
    implicit S: ContextShift[IO]): Resource[IO, BatchEmitter] = {

    val create: IO[BatchEmitter] = for {
      buffer <- Ref.of[IO, Vector[EmitterPayload]](Vector.empty[EmitterPayload])
      queue  <- Queue.bounded[IO, CollectorRequest](10)
      sink = send(collector, callback, client, queue)
      queueFiber <- queue.dequeue.observe(sink).compile.drain.start
    } yield
      new BatchEmitter {
        def send(event: EmitterPayload): IO[Unit] = addItem(bufferConfig)(queue, buffer)(event)
        def fiber: Fiber[IO, Unit]                = queueFiber
      }
    Resource.make(create)(_.fiber.cancel)
  }
}
