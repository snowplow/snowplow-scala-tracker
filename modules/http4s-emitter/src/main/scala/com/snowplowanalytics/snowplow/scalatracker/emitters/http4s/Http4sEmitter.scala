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

import cats.{Monad, MonadThrow}
import cats.effect.{Async, Clock, Concurrent, Fiber, Resource, Sync, Temporal}
import cats.effect.std.{Queue, Random}
import cats.implicits._
import com.snowplowanalytics.snowplow.scalatracker.{Buffer, Emitter, Payload}
import com.snowplowanalytics.snowplow.scalatracker.Buffer.Action
import com.snowplowanalytics.snowplow.scalatracker.Emitter._
import fs2.{Pipe, Stream}
import org.http4s.{MediaType, Method, Uri, Request => HttpRequest}
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._

object Http4sEmitter {

  /** Build an emitter which uses an fs2 stream for internal event processing.
    *
    * @param collector The [[EndpointParams]] for the snowplow collector
    * @param client An http4s client used to send events.
    * @param bufferConfig Configures buffering of events, before they are sent to the collector in larger batches.
    * @param retryPolicy Configures how the emiiter retries sending events to the collector in case of failure.
    * @param queuePolicy Configures how the emitter's `send` method behaves when the queue is full.
    * @param callback optional callback executed after each sent event, or failed attempt
    * @param shutdownTimeout A timeout triggered when the `Resource` returned by this function is closed.
    * Pending events not sent within this timeout will be dropped. `None` means to wait indefinitely for sending
    * pending events when shutting down.
    */
  def build[F[_]: Async: Random](
    collector: EndpointParams,
    client: Client[F],
    bufferConfig: BufferConfig              = BufferConfig.Default,
    retryPolicy: RetryPolicy                = RetryPolicy.Default,
    queuePolicy: EventQueuePolicy           = EventQueuePolicy.Default,
    callback: Option[Callback[F]]           = None,
    shutdownTimeout: Option[FiniteDuration] = None
  ): Resource[F, Emitter[F]] =
    Resource {
      for {
        queue <- queueForPolicy[F](queuePolicy)
        fiber <- Concurrent[F].start(drainQueue(queue, client, collector, bufferConfig, retryPolicy, callback))
      } yield instance(queue, queuePolicy) -> shutdown(fiber, queue, shutdownTimeout)
    }

  private def instance[F[_]: MonadThrow](queue: Queue[F, Action], queuePolicy: EventQueuePolicy): Emitter[F] =
    new Emitter[F] {
      override def send(event: Payload): F[Unit] =
        queuePolicy match {
          case EventQueuePolicy.UnboundedQueue | EventQueuePolicy.BlockWhenFull(_) =>
            queue.offer(Action.Enqueue(event))
          case EventQueuePolicy.IgnoreWhenFull(_) =>
            queue.tryOffer(Action.Enqueue(event)).void
          case EventQueuePolicy.ErrorWhenFull(limit) =>
            queue.tryOffer(Action.Enqueue(event)).flatMap {
              case true  => MonadThrow[F].unit
              case false => MonadThrow[F].raiseError(new EventQueuePolicy.EventQueueException(limit))
            }
        }

      override def flushBuffer(): F[Unit] =
        queue.offer(Action.Flush)
    }

  private def queueForPolicy[F[_]: Concurrent](policy: EventQueuePolicy): F[Queue[F, Action]] =
    policy.tryLimit match {
      case Some(max) => Queue.bounded(max)
      case None      => Queue.unbounded
    }

  private def shutdown[F[_]: Temporal](
    fiber: Fiber[F, Throwable, Unit],
    queue: Queue[F, Action],
    timeout: Option[FiniteDuration]
  ): F[Unit] =
    timeout match {
      case Some(t) => shutdownWithTimeout(fiber, queue, t)
      case None    => queue.offer(Action.Terminate) *> fiber.join.void
    }

  private def shutdownWithTimeout[F[_]: Temporal](
    fiber: Fiber[F, Throwable, Unit],
    queue: Queue[F, Action],
    timeout: FiniteDuration
  ): F[Unit] =
    // First try to enqueue the terminate event. This will block if the queue is full.
    Concurrent[F].racePair(queue.offer(Action.Terminate), Temporal[F].sleep(timeout)).flatMap {
      case Left((_, timerFiber)) =>
        // We successfully enqueued the terminate event.
        // Next, wait for pending events to be sent to the collector.
        Concurrent[F].racePair(fiber.join, timerFiber.join).flatMap {
          case Left(_) =>
            // Pending events were successfully handled within the time limit
            timerFiber.cancel
          case Right(_) =>
            // We ran out of time waiting for pending events to get sent.
            // Cancel any processing of unsent events.
            fiber.cancel
        }
      case Right(_) =>
        // We ran out of time waiting to enqueue the terminate event.
        // Cancel any processing of the queue.
        fiber.cancel
    }

  private def drainQueue[F[_]: Async: Random](
    queue: Queue[F, Action],
    client: Client[F],
    collector: EndpointParams,
    bufferConfig: BufferConfig,
    retryPolicy: RetryPolicy,
    callback: Option[Callback[F]]
  ): F[Unit] =
    Stream
      .fromQueueUnterminated(queue)
      .takeThrough(_ != Action.Terminate)
      .through(bufferEvents(bufferConfig))
      .evalMap(sendRequest(client, collector, retryPolicy, callback))
      .compile
      .drain

  private def bufferEvents[F[_]](bufferConfig: BufferConfig): Pipe[F, Action, Request] =
    _.mapAccumulate(Buffer(bufferConfig)) {
      case (buffer, action) => buffer.handle(action)
    }.collect {
      case (_, Some(flushed)) => flushed
    }

  private def sendRequest[F[_]: Async: Random](
    client: Client[F],
    collector: EndpointParams,
    retryPolicy: RetryPolicy,
    callback: Option[Callback[F]]
  ): Request => F[Unit] =
    Monad[F].tailRecM(_) { request =>
      attemptSend(client, collector, request).flatTap(invokeCallback(callback, collector, request, _)).flatMap {
        result =>
          if (result.isSuccess || request.isFailed(retryPolicy)) {
            Monad[F].pure(Right(()))
          } else {
            for {
              seed <- Random[F].nextDouble
              _    <- Temporal[F].sleep(RetryPolicy.getDelay(request.attempt, seed).millis)
            } yield request.updateAttempt.asLeft
          }
      }
    }

  private def attemptSend[F[_]: MonadThrow: Clock](
    client: Client[F],
    collector: EndpointParams,
    request: Request
  ): F[Result] =
    Clock[F].realTime.map(_.toMillis).flatMap { dtm =>
      val httpRequest = request.updateStm(dtm) match {
        case Request.Buffered(_, payload) =>
          val body = Stream.emits(Payload.postPayload(payload).getBytes).covary[F]
          HttpRequest[F](Method.POST, Uri.unsafeFromString(collector.getPostUri))
            .withEntity(body)
            .withContentType(`Content-Type`(MediaType.application.json))
        case Request.Single(_, payload) =>
          val uri =
            Uri.unsafeFromString(collector.getGetUri).setQueryParams {
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
        .handleError(Result.TrackerFailure)
    }

  lazy val logger: Logger = LoggerFactory.getLogger(getClass.getName)

  private def invokeCallback[F[_]: Sync](
    callback: Option[Callback[F]],
    collector: EndpointParams,
    request: Request,
    result: Result
  ): F[Unit] =
    callback match {
      case Some(cb) =>
        cb(collector, request, result).handleErrorWith { throwable =>
          if (logger.isWarnEnabled)
            Sync[F].delay(
              logger.warn(s"Snowplow Tracker bounded to ${collector.getUri} failed to execute callback", throwable)
            )
          else
            Sync[F].unit
        }
      case None => Monad[F].unit
    }

}
