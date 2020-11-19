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

import cats.{Monad, MonadError}
import cats.implicits._
import cats.effect.{Concurrent, Fiber, Resource, Sync, Timer}
import fs2.{Pipe, Stream}
import fs2.concurrent.{Dequeue, Enqueue, Queue}
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`
import org.http4s.{MediaType, Method, Request => HttpRequest, Uri}
import scala.concurrent.duration._
import scala.util.Random

import com.snowplowanalytics.snowplow.scalatracker.{Buffer, Emitter, Payload}
import com.snowplowanalytics.snowplow.scalatracker.Emitter._
import com.snowplowanalytics.snowplow.scalatracker.Buffer.Action

object Http4sEmitter {

  def build[F[_]: Concurrent: Timer](collector: EndpointParams,
                                     client: Client[F],
                                     bufferConfig: BufferConfig    = BufferConfig.Default,
                                     retryPolicy: RetryPolicy      = RetryPolicy.Default,
                                     queuePolicy: EventQueuePolicy = EventQueuePolicy.Default,
                                     callback: Option[Callback[F]] = None): Resource[F, Emitter[F]] =
    Resource {
      for {
        queue <- queueForPolicy[F](queuePolicy)
        fiber <- Concurrent[F].start(drainQueue(queue, client, collector, bufferConfig, retryPolicy, callback))
      } yield instance(queue) -> shutdown(fiber, queue)
    }

  private def instance[F[_]](queue: Enqueue[F, Action]): Emitter[F] =
    new Emitter[F] {
      override def send(event: Payload): F[Unit] =
        queue.enqueue1(Action.Enqueue(event))

      override def flushBuffer(): F[Unit] =
        queue.enqueue1(Action.Flush)
    }

  private def queueForPolicy[F[_]: Concurrent](policy: EventQueuePolicy): F[Queue[F, Action]] =
    policy match {
      case EventQueuePolicy.UnboundedQueue      => Queue.unbounded
      case EventQueuePolicy.BlockWhenFull(max)  => Queue.bounded(max)
      case EventQueuePolicy.IgnoreWhenFull(max) => Queue.bounded(max)
      case EventQueuePolicy.ErrorWhenFull(max)  => Queue.bounded(max)
    }

  private def shutdown[F[_]: Monad](fiber: Fiber[F, Unit], queue: Enqueue[F, Action]): F[Unit] =
    queue.enqueue1(Action.Terminate) *> fiber.join

  private def drainQueue[F[_]: Sync: Timer](queue: Dequeue[F, Action],
                                            client: Client[F],
                                            collector: EndpointParams,
                                            bufferConfig: BufferConfig,
                                            retryPolicy: RetryPolicy,
                                            callback: Option[Callback[F]]): F[Unit] =
    Sync[F].delay(new Random()).flatMap { rng =>
      queue.dequeue
        .takeThrough(_ != Action.Terminate)
        .through(bufferEvents(bufferConfig))
        .evalMap(sendRequest(client, collector, retryPolicy, rng, callback))
        .compile
        .drain
    }

  private def bufferEvents[F[_]](bufferConfig: BufferConfig): Pipe[F, Action, Request] =
    _.mapAccumulate(Buffer(bufferConfig)) {
      case (buffer, action) => buffer.handle(action)
    }.collect {
      case (_, Some(flushed)) => flushed
    }

  private def sendRequest[F[_]: Sync: Timer](client: Client[F],
                                             collector: EndpointParams,
                                             retryPolicy: RetryPolicy,
                                             rng: Random,
                                             callback: Option[Callback[F]]): Request => F[Unit] =
    Sync[F].tailRecM(_) { request =>
      attemptSend(client, collector, request)
        .flatTap(invokeCallback(callback, collector, request, _))
        .flatMap { result =>
          if (result.isSuccess || request.isFailed(retryPolicy)) {
            Sync[F].pure(Right(()))
          } else {
            for {
              seed <- Sync[F].delay(rng.nextDouble())
              _    <- Timer[F].sleep(RetryPolicy.getDelay(request.attempt, seed).millis)
            } yield request.updateAttempt.asLeft
          }
        }
    }

  private def attemptSend[F[_]: MonadError[?[_], Throwable]: Timer](client: Client[F],
                                                                    collector: EndpointParams,
                                                                    request: Request): F[Result] =
    Timer[F].clock.realTime(MILLISECONDS).flatMap { dtm =>
      val httpRequest = request.updateStm(dtm) match {
        case Request.Buffered(_, payload) =>
          val body = Stream.emits(Payload.postPayload(payload).getBytes).covary[F]
          HttpRequest[F](Method.POST, Uri.unsafeFromString(collector.getPostUri))
            .withEntity(body)
            .withContentType(`Content-Type`(MediaType.application.json))
        case Request.Single(_, payload) =>
          val uri =
            Uri
              .unsafeFromString(collector.getGetUri)
              .setQueryParams {
                payload.toMap.map { case (k, v) => k -> List(v) }
              }
          HttpRequest[F](Method.GET, uri)
      }

      client
        .status(httpRequest)
        .map { status =>
          if (status.isSuccess) Result.Success(status.code)
          else Result.Failure(status.code)
        }
        .handleError(Result.TrackerFailure(_))
    }

  private def invokeCallback[F[_]: MonadError[?[_], Throwable]](callback: Option[Callback[F]],
                                                                collector: EndpointParams,
                                                                request: Request,
                                                                result: Result): F[Unit] =
    callback match {
      case Some(cb) =>
        cb(collector, request, result).handleError(_ => ())
      case None => Monad[F].unit
    }

}
