package com.snowplowanalytics.snowplow.scalatracker.emitters.http4s

import scala.concurrent.duration._
import cats.implicits._
import cats.effect.{ContextShift, IO, Resource, Timer}
import fs2.Stream
import fs2.concurrent.Queue
import org.http4s.Request
import org.http4s.client.blaze.BlazeClientBuilder
import com.snowplowanalytics.snowplow.scalatracker.{Emitter, Tracker}
import org.specs2.Specification

import scala.util.Random

class HttpEmitterSpec extends Specification {
  def is = s2"""
  Perform specified amount of requests e1
  Perform specified amount of requests even with POST e2
  Perform streaming flush $e3
  """

  def e1 = {
    val requests = HttpEmitterSpec.perform(Emitter.BufferConfig.NoBuffering) { tracker =>
      tracker.trackPageView("http://example.com") *>
        tracker.trackPageView("http://example.com") *>
        tracker.trackPageView("http://example.com")
    }

    requests.unsafeRunSync() must haveLength(3)
  }

  def e2 = {
    val requests = HttpEmitterSpec.perform(Emitter.BufferConfig.EventsCardinality(2)) { tracker =>
      tracker.trackPageView("http://example.com") *>
        tracker.trackPageView("http://example.com") *>
        tracker.trackPageView("http://example.com")
    }

    requests.unsafeRunSync() must haveLength(2)
  }

  def e3 = {
    def send(tracker: Tracker[IO])(i: Int): IO[Unit] =
      tracker.trackPageView("http://example.com", Some(i.toString))

    implicit val cs = IO.contextShift(scala.concurrent.ExecutionContext.global)

    val requests = HttpEmitterSpec.perform(Emitter.BufferConfig.NoBuffering) { tracker =>
      val stream = Stream
        .emits[IO, Int](List(1, 2, 3))
        .evalMap[IO, Unit] { send(tracker) }
      val flattenStream = Stream(stream, stream, stream).covary[IO].parJoin(2)
      flattenStream.compile.drain
    }

    requests.unsafeRunSync().flatMap(_.params.get("page")) must beEqualTo(
      List(1, 1, 2, 3, 2, 3, 1, 2, 3).map(_.toString))
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

  def getTracker(bufferConfig: Emitter.BufferConfig): Resource[IO, Tracker[IO]] =
    for {
      client  <- getClient
      emitter <- HttpEmitter.start(client, Collector.Params, bufferConfig)
      tracker = Tracker[IO](emitter, "emitter-spec", "scala-tracker")
    } yield tracker

  def perform(bufferConfig: Emitter.BufferConfig)(action: Tracker[IO] => IO[Unit]) = {
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
