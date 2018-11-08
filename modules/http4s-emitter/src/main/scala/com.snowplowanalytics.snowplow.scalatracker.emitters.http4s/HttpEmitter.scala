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
package com.snowplowanalytics.snowplow.scalatracker
package emitters.http4s

import scala.util.Random
import scala.concurrent.duration._

import cats.{Applicative, ApplicativeError}
import cats.effect._
import cats.effect.implicits._
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.apply._

import fs2.{Pipe, Sink, Stream}
import fs2.concurrent.{Enqueue, Queue}

import org.http4s.{Method, Request, Response, Uri}
import org.http4s.client.Client

import Emitter._

trait HttpEmitter[F[_]] extends Emitter[F] {
  def send(event: Payload): F[Unit]
  def flush: F[Unit]

  private[scalatracker] def mainHandle: Fiber[F, Unit]
  private[scalatracker] def retryHandle: Fiber[F, Unit]
}

object HttpEmitter {

  /**
   * Primary constructor, creating a queue and starting a pulling thread
   *
   * @param client http4s client with blocking or non-blocking semantics
   * @param collector collector endpoint configuration
   * @param config buffer configuration (e.g. GET or POST)
   * @param callback optional callback
   * @return resource that will free the pulling thread after releasing
   */
  def start[F[_]: Concurrent: Timer](client: Client[F],
                                     collector: EndpointParams,
                                     config: BufferConfig,
                                     callback: Option[Callback[F]],
                                     retryQueueSize: Int): Resource[F, HttpEmitter[F]] = {
    val create: F[HttpEmitter[F]] = for {
      rng          <- Sync[F].delay(new Random())
      buffer       <- getBufferQueue(config)
      requestQueue <- Queue.unbounded[F, Request]
      bufferFiber  <- runBuffer(buffer, requestQueue, config)
      retryQueue   <- Queue.circularBuffer[F, Request](retryQueueSize)
      sink = pull(client, rng, collector, callback, requestQueue, retryQueue)
      queueFiber <- requestQueue.dequeue.to(sink).compile.drain.foreverM[Unit].start
    } yield HttpEmitter(submit(config, requestQueue, buffer), queueFiber, bufferFiber, flush(requestQueue, buffer))

    Resource.make(create)(e => e.flush *> e.mainHandle.cancel *> e.retryHandle.cancel)
  }

  def start[F[_]: Concurrent: Timer](client: Client[F],
                                     collector: EndpointParams,
                                     config: BufferConfig): Resource[F, HttpEmitter[F]] =
    start(client, collector, config, None, 1024)

  /** Send payload as HTTP request */
  def send[F[_]](client: Client[F], collector: EndpointParams, request: Request, dtm: Long)(
    implicit F: ApplicativeError[F, Throwable]): F[Emitter.Result] = {
    val httpRequest = request.updateStm(dtm) match {
      case Request.Post(_, payload) =>
        val body = Stream.emits(Emitter.postPayload(payload).getBytes).covary[F]
        Request[F](Method.POST, Uri.unsafeFromString(collector.getPostUri), body = body)
      case Request.Get(_, payload) =>
        val uri = Uri.unsafeFromString(collector.getGetUri).setQueryParams(payload.mapValues(v => List(v)))
        Request[F](Method.GET, uri)
    }

    client
      .fetch(httpRequest)(response => toResult(response))
      .attempt
      .map(e => e.fold(Result.TrackerFailure.apply, identity))
  }

  /** Pull a queue for payloads and send them to a collector, re-add in case of failure */
  def pull[F[_]: Concurrent: Timer](client: Client[F],
                                    rng: Random,
                                    collector: EndpointParams,
                                    callback: Option[Callback[F]],
                                    queue: Enqueue[F, Request],
                                    retryQueue: Enqueue[F, Request]): Sink[F, Request] =
    _.evalMap[F, Unit] { payload =>
      val finish: Result => F[Unit] =
        callback.fold((_: Result) => Sync[F].unit)(cb => r => cb(collector, payload, r))
      for {
        dtm    <- implicitly[Timer[F]].clock.realTime(MILLISECONDS)
        result <- send(client, collector, payload, dtm)
        _      <- finish(result)
        _ <- result match {
          case Result.Success(_) => Sync[F].unit
          case failure =>
            for {
              resent <- backToQueue(rng, retryQueue, payload)
              _      <- if (resent) Applicative[F].unit else finish(Result.RetriesExceeded(failure))
            } yield ()
        }
      } yield ()
    }

  def flush[F[_]: Async](queue: Enqueue[F, Request], buffer: Queue[F, Payload]) = {
    val stream = Stream
      .repeatEval(buffer.tryDequeueChunk1(10))
      .takeWhile(_.isDefined)
      .flatMap {
        case Some(chunk) => Stream.emit(Request(chunk.toList))
        case None        => Stream.empty
      }
    stream.compile.drain
  }

  def wrapPayloads[F[_]: Async](i: Int): Pipe[F, Payload, Request] =
    (buffer: Stream[F, Payload]) => {
      buffer.chunkLimit(i).map { chunk =>
        Request(chunk.toList)
      }
    }

  /** Depending on buffer configuration, either buffer an event or send it straight away */
  def submit[F[_]: Async](bufferConfig: BufferConfig,
                          queue: Enqueue[F, Request],
                          buffer: Queue[F, Payload])(event: Payload) =
    bufferConfig match {
      case BufferConfig.EventsCardinality(_) =>
        buffer.enqueue1(event)
      case BufferConfig.PayloadSize(bytesAllowed) =>
        for {
          // Find more efficient solution
          // but probably this won't work at all
          // E.g. buffer is infinite stream and cannot be drained to finite seq
          current <- buffer.dequeue.compile.toVector
          isAllowed = canBuffer(current, event, bytesAllowed)
          _ = if (isAllowed) buffer.enqueue(bufferEmit(event +: current)).compile.drain
          else buffer.enqueue(bufferEmit(current)).compile.drain *> buffer.enqueue1(event)
        } yield ()
      case BufferConfig.NoBuffering =>
        queue.enqueue1(Request(event))
    }

  /** false if need to send existing, true if okay to add */
  def canBuffer(existing: Seq[Payload], newcomer: Payload, allowed: Int): Boolean =
    Emitter.postPayload(existing :+ newcomer).getBytes.length < allowed

  /** Run buffering pipe if necessary */
  def runBuffer[F[_]: Concurrent](buffer: Queue[F, Payload],
                                  queue: Queue[F, Request],
                                  config: BufferConfig) =
    config match {
      case BufferConfig.EventsCardinality(size) =>
        Stream
          .repeatEval(Sync[F].pure(size))
          .through[F, Payload](buffer.dequeueBatch)
          .through(wrapPayloads(size))
          .to(queue.enqueue)
          .compile
          .drain
          .start
      case BufferConfig.NoBuffering =>
        Sync[F].pure(Fiber(Sync[F].unit, Sync[F].unit))
    }

  private def bufferEmit[F[_]](payloads: Vector[Payload]) =
    Stream.emits[F, Payload](payloads)

  private[scalatracker] def apply[F[_]](f: Payload => F[Unit],
                                        main: Fiber[F, Unit],
                                        retry: Fiber[F, Unit],
                                        end: F[Unit]): HttpEmitter[F] =
    new HttpEmitter[F] {
      def send(event: Payload): F[Unit] = f(event)
      val flush: F[Unit]                       = end

      val mainHandle: Fiber[F, Unit]  = main
      val retryHandle: Fiber[F, Unit] = retry

    }

  /** Add a payload to queue or reject as wasted */
  private def backToQueue[F[_]: Concurrent: Timer](rng: Random,
                                                   retryQueue: Enqueue[F, Request],
                                                   retry: Request): F[Boolean] =
    if (retry.isFailed) Applicative[F].pure(false)
    else
      for {
        seed <- Sync[F].delay(rng.nextDouble())
        delay = getDelay(retry.attempt, seed)
        _ <- implicitly[Timer[F]].sleep(delay.millis)
        _ <- retryQueue.enqueue1(retry) // retryQueue supposed to be circular, so no blocking happens
        // TODO: we need to have a callback for discarded events
      } yield true

  /** Transform http4s [[Response]] to tracker's [[Result]] */
  private def toResult[F[_]: Applicative](response: Response[F]): F[Result] =
    if (response.status.code == 200) Applicative[F].pure(Result.Success(200))
    else Applicative[F].pure(Result.Failure(response.status.code))

  private def getBufferQueue[F[_]: Concurrent](config: BufferConfig): F[Queue[F, Payload]] =
    config match {
      case BufferConfig.EventsCardinality(size) =>
        Queue.bounded[F, Payload](size * 2)
      case BufferConfig.PayloadSize(_) =>
        Queue.unbounded[F, Payload]
      case BufferConfig.NoBuffering =>
        Queue.bounded[F, Payload](1)
    }
}
