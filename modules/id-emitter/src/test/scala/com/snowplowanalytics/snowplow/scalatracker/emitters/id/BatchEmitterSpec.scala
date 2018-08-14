package com.snowplowanalytics.snowplow.scalatracker.emitters.id

import scala.concurrent.duration._

import com.snowplowanalytics.snowplow.scalatracker.emitters.id.RequestProcessor.CollectorParams
import org.specs2.Specification
import org.specs2.mock.Mockito

class BatchEmitterSpec extends Specification with Mockito {

  override def is = s2"""

    buffer should not flush before reaching bufferSize $e1
    buffer should flush after reaching bufferSize      $e2

  """

  val payload = Map("foo" -> "bar", "bar" -> "foo")

  def e1 = {
    val processor = spy(new RequestProcessor)
    doNothing.when(processor).submit(any(), any(), any(), any(), any())

    val params  = CollectorParams.construct("example.com")
    val emitter = new AsyncBatchEmitter(scala.concurrent.ExecutionContext.global, params, 3, None, processor)
    emitter.startWorker()

    emitter.send(payload)
    emitter.send(payload)

    Thread.sleep(100)
    there were noCallsTo(processor)
  }

  def e2 = {
    val processor = spy(new RequestProcessor)
    doNothing.when(processor).submit(any(), any(), any(), any(), any())

    val params  = CollectorParams.construct("example.com")
    val emitter = new AsyncBatchEmitter(scala.concurrent.ExecutionContext.global, params, 3, None, processor)
    emitter.startWorker()

    emitter.send(payload)
    emitter.send(payload)
    emitter.send(payload)

    eventually(there was one(processor).submit(any(), any(), any(), any(), any()))
  }
}
