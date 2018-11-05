package com.snowplowanalytics.snowplow.scalatracker.emitters.http4s

import scala.concurrent.duration._

import cats.implicits._
import cats.effect.{ContextShift, IO, Resource, Timer}

import fs2.Stream
import fs2.concurrent.Queue

import org.http4s.Request
import org.http4s.client.blaze.BlazeClientBuilder
import com.snowplowanalytics.snowplow.scalatracker.Tracker
import com.snowplowanalytics.snowplow.scalatracker.Emitter.BufferConfig

import org.specs2.Specification

class PureEmitterSpec extends Specification {
  def is = s2"""
  Perform specified amount of requests $e1
  """

  def e1 = {
    val requests = PureEmitterSpec.perform { tracker =>
      tracker.trackPageView("http://example.com") *>
        tracker.trackPageView("http://example.com") *>
        tracker.trackPageView("http://example.com")
    }

    requests.unsafeRunSync() must haveLength(3)
  }
}

object PureEmitterSpec {
  val ec                            = scala.concurrent.ExecutionContext.global
  implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO]     = IO.timer(ec)

  def getTracker: Resource[IO, Tracker[IO]] =
    for {
      client  <- BlazeClientBuilder[IO](ec).resource
      emitter <- PureEmitter.start(client, Collector.Params, BufferConfig.Get)
      tracker = Tracker[IO](emitter, "emitter-spec", "scala-tracker")
    } yield tracker

  def perform(action: Tracker[IO] => IO[Unit]) = {
    val resources: Resource[IO, (Queue[IO, Request[IO]], Tracker[IO])] = for {
      queue   <- Collector.getServerLog
      tracker <- PureEmitterSpec.getTracker
    } yield (queue, tracker)

    resources.use {
      case (queue, tracker) =>
        action(tracker) *> IO.sleep(1.second) *> flushQueue(queue)
    }
  }

  def flushQueue[A](queue: Queue[IO, A]): IO[List[A]] =
    Stream
      .repeatEval(queue.tryDequeueChunk1(10))
      .takeWhile(_.isDefined)
      .flatMap {
        case None        => Stream.empty
        case Some(chunk) => Stream.chunk(chunk)
      }
      .compile
      .toList
}
