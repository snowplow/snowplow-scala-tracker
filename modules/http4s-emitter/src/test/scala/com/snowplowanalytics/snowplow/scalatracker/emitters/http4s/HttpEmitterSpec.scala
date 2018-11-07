package com.snowplowanalytics.snowplow.scalatracker.emitters.http4s

import scala.concurrent.duration._
import cats.implicits._
import cats.effect.{ContextShift, IO, Resource, Timer}
import fs2.Stream
import fs2.concurrent.Queue
import org.http4s.Request
import org.http4s.client.blaze.BlazeClientBuilder
import com.snowplowanalytics.snowplow.scalatracker.Tracker
import com.snowplowanalytics.snowplow.scalatracker.Emitter.{BufferConfig, CollectorRequest}
import org.specs2.Specification

import scala.util.Random

class HttpEmitterSpec extends Specification {
  def is = s2"""
  Perform specified amount of requests e1
  Perform specified amount of requests even with POST $e2
  """

  def e1 = {
    val requests = HttpEmitterSpec.perform(BufferConfig.Get) { tracker =>
      tracker.trackPageView("http://example.com") *>
        tracker.trackPageView("http://example.com") *>
        tracker.trackPageView("http://example.com")
    }

    requests.unsafeRunSync() must haveLength(3)
  }

  def e2 = {
    val requests = HttpEmitterSpec.perform(BufferConfig.Post(2)) { tracker =>
      tracker.trackPageView("http://example.com") *>
        tracker.trackPageView("http://example.com") *>
        tracker.trackPageView("http://example.com")
    }

    requests.unsafeRunSync() must haveLength(2)
  }
}

object HttpEmitterSpec {
  val ec                            = scala.concurrent.ExecutionContext.global
  implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO]     = IO.timer(ec)

  def range(rng: Random, start: Int, end: Int): IO[Int] =
    IO {
      val rnd = new scala.util.Random
      start + rnd.nextInt((end - start) + 1)
    }

  def getClient =
    BlazeClientBuilder[IO](ec).resource

  def getTracker(bufferConfig: BufferConfig): Resource[IO, Tracker[IO]] =
    for {
      client  <- getClient
      emitter <- HttpEmitter.start(client, Collector.Params, bufferConfig)
      tracker = Tracker[IO](emitter, "emitter-spec", "scala-tracker")
    } yield tracker

  def perform(bufferConfig: BufferConfig)(action: Tracker[IO] => IO[Unit]) = {
    val resources: Resource[IO, (Queue[IO, Request[IO]], Tracker[IO])] = for {
      queue   <- Collector.getServerLog
      tracker <- HttpEmitterSpec.getTracker(bufferConfig)
    } yield (queue, tracker)

    resources.use {
      case (queue, tracker) =>
        action(tracker) *> IO.sleep(3.second) *> flushQueue(queue)
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
