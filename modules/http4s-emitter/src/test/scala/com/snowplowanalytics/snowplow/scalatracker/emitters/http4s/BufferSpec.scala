package com.snowplowanalytics.snowplow.scalatracker.emitters.http4s

import org.specs2.Specification

import fs2.{Chunk, Stream}

class BufferSpec extends Specification {
  def is = s2"""
  Group correctly $e1
  Group List correctly $e2
  """

  def e1 = {
    val result = Stream.emits(Seq(1, 2, 3, 4, 5)).through(Buffer.notExceed(identity[Int], 4)).toList

    println(result)
    ko
  }

  def e2 = {
    val a = Buffer.segment(identity[Int], 4)(Chunk.seq(List(1, 2, 3, 4, 5)))
    ko
  }

}
