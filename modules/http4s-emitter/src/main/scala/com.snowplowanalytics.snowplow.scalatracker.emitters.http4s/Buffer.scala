package com.snowplowanalytics.snowplow.scalatracker.emitters.http4s

import cats.effect.Async
import com.snowplowanalytics.snowplow.scalatracker.Emitter
import fs2.{Chunk, Pipe, Pull, Stream}

object Buffer {

  def notExceed[F[_], A](getSize: A => Int, limit: Int): Pipe[F, A, Chunk[A]] = {
    def go(s: Stream[F, A]): Pull[F, Chunk[A], Unit] =
      s.pull.uncons.flatMap {
        case Some((chunk, tail)) =>
          val (buffers, remaining) = segment(getSize, limit)(chunk)
          if (buffers.isEmpty)
            Pull.output(Chunk.singleton(remaining))
          else
            Pull.output(buffers) >> go(Stream.chunk(remaining) ++ tail)
        case None => Pull.done
      }

    s =>
      go(s).stream
  }

  def emit[F[_]](payloads: Chunk[Emitter.Payload]) =
    Stream.emit[F, Emitter.Request](Emitter.Request(payloads.toList))

  def wrapPayloads[F[_]: Async](i: Int): Pipe[F, Emitter.Payload, Emitter.Request] =
    (buffer: Stream[F, Emitter.Payload]) =>
      buffer.chunkLimit(i).map { chunk =>
        Emitter.Request(chunk.toList)
    }

  /** Split list of elements into segments, so that any segment would not exceed a limit */
  def segment[A](getSize: A => Int, limit: Int)(list: Chunk[A]) = {
    val result = list.foldLeft(Segment.Start[A]) {
      case (group, element) =>
        val elementSize = getSize(element)
        if (group.size + elementSize >= limit)
          if (elementSize >= limit)
            group.shiftOversize(element)
          else
            group.shift(element, elementSize)
        else group.add(element, group.size + elementSize)
    }

    (Chunk.seq(result.accumulator.reverse).map(Chunk.seq), Chunk.seq(result.currentChunk))
  }

  private case class Segment[A](size: Int, accumulator: List[List[A]], currentChunk: List[A]) {
    def shift(element: A, newSize: Int): Segment[A] =
      Segment(newSize, currentChunk.reverse :: accumulator, element :: Nil)

    def shiftOversize(element: A): Segment[A] = currentChunk match {
      case Nil   => Segment(0, List(element) :: accumulator, Nil)
      case other => Segment(0, List(element) :: other :: accumulator, Nil)
    }

    def add(element: A, newSize: Int): Segment[A] =
      Segment(newSize, accumulator, element :: currentChunk)
  }

  private object Segment {
    def Start[A] = Segment[A](0, List.empty, List.empty)
  }
}
