package com.snowplowanalytics.snowplow.scalatracker
package emitters.http4s

import cats.{Applicative, ApplicativeError, Monad}
import cats.effect._
import cats.effect.concurrent.Ref
import cats.effect.implicits._
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._

import fs2.{Sink, Stream}
import fs2.concurrent.{Enqueue, Queue}

import org.http4s.{Method, Request, Response, Uri}
import org.http4s.client.Client

import Emitter._

trait BatchEmitter[F[_]] extends Emitter[F] {
  def send(event: EmitterPayload): F[Unit]

  private[scalatracker] def handle: Fiber[F, Unit]
}

object BatchEmitter {

  /**
   * Primary constructor, creating a queue and starting a pulling thread
   *
   * @param client http4s client with blocking or non-blocking semantics
   * @param collector collector endpoint configuration
   * @param config buffer configuration (e.g. GET or POST)
   * @param callback optional callback
   * @return resource that will free the pulling thread after releasing
   */
  def start[F[_]: Concurrent](client: Client[F],
                              collector: CollectorParams,
                              config: BufferConfig,
                              callback: Option[Callback[F]]): Resource[F, BatchEmitter[F]] = {
    val create: F[BatchEmitter[F]] = for {
      buffer <- Ref.of[F, Vector[EmitterPayload]](Vector.empty[EmitterPayload])
      queue  <- Queue.bounded[F, CollectorRequest](10)
      sink = pull(client, collector, callback, queue)
      queueFiber <- queue.dequeue.observe(sink).compile.drain.start
    } yield BatchEmitter(submit(config)(queue, buffer), queueFiber)
    Resource.make(create)(_.handle.cancel)
  }

  /** Send payload as HTTP request */
  def send[F[_]](client: Client[F], collector: CollectorParams, payload: CollectorRequest)(
    implicit F: ApplicativeError[F, Throwable]): F[Emitter.Result] = {
    val request = payload match {
      case CollectorRequest.Post(_, payload) =>
        val body = Stream.emits(Emitter.postPayload(payload).getBytes).covary[F]
        Request[F](Method.POST, Uri.unsafeFromString(collector.getPostUri), body = body)
      case CollectorRequest.Get(_, payload) =>
        val uri = Uri.unsafeFromString(collector.getGetUri).setQueryParams(payload.mapValues(v => List(v)))
        Request[F](Method.GET, uri)
    }

    client
      .fetch(request)(response => toResult(response))
      .attempt
      .map(e => e.fold(Result.TrackerFailure.apply, identity))
  }

  /** Pull a queue for payloads and send them to a collector, re-add in case of failure */
  def pull[F[_]: Sync](client: Client[F],
                       collector: CollectorParams,
                       callback: Option[Callback[F]],
                       queue: Enqueue[F, CollectorRequest]): Sink[F, CollectorRequest] =
    _.evalMap[F, Unit] { payload =>
      val finish: Result => F[Unit] =
        callback.fold((_: Result) => Sync[F].unit)(cb => r => cb(collector, payload, r))
      for {
        result <- send(client, collector, payload)
        _      <- finish(result)
        _ <- result match {
          case Result.Success(_) => Sync[F].unit
          case failure =>
            for {
              resent <- backToQueue(queue, payload)
              _      <- if (resent) Sync[F].unit else finish(Result.RetriesExceeded(failure))
            } yield ()
        }
      } yield ()
    }

  /** Depending on buffer configuration, either buffer an event or send it straight away */
  def submit[F[_]: Monad](bufferConfig: BufferConfig)(queue: Enqueue[F, CollectorRequest],
                                                      buffer: Ref[F, Vector[EmitterPayload]])(event: EmitterPayload) =
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

  private[scalatracker] def apply[F[_]](f: EmitterPayload => F[Unit], fiber: Fiber[F, Unit]): BatchEmitter[F] =
    new BatchEmitter[F] {
      def send(event: EmitterPayload): F[Unit] = f(event)
      def handle: Fiber[F, Unit]               = fiber
    }

  /** Add a payload to queue or reject as wasted */
  private def backToQueue[F[_]: Applicative](queue: Enqueue[F, CollectorRequest],
                                             request: CollectorRequest): F[Boolean] =
    if (request.isFailed) Applicative[F].pure(false)
    else queue.enqueue1(request).as(true)

  /** Transform http4s [[Response]] to tracker's [[Result]] */
  private def toResult[F[_]: Applicative](response: Response[F]): F[Result] =
    if (response.status.code == 200) Applicative[F].pure(Result.Success(200))
    else Applicative[F].pure(Result.Failure(response.status.code))
}
