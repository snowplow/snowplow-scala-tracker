/*
 * Copyright (c) 2015-2020 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.snowplow.scalatracker.emitters.id

import java.util.concurrent.BlockingQueue

import scala.concurrent.duration._
import com.snowplowanalytics.snowplow.scalatracker.Emitter.EndpointParams
import com.snowplowanalytics.snowplow.scalatracker.emitters.id.RequestProcessor.{Callback, CollectorRequest}
import org.specs2.Specification
import org.specs2.mock.Mockito

import scala.concurrent.ExecutionContext

class BatchEmitterSpec extends Specification with Mockito {

  override def is = s2"""

    AsyncBatchEmitter's buffer should not flush before reaching bufferSize $e1
    AsyncBatchEmitter's buffer should flush after reaching bufferSize      $e2
    SyncBatchEmitter's buffer should not flush before reaching bufferSize  $e3
    SyncBatchEmitter's buffer should flush after reaching bufferSize       $e4

  """

  val payload = Map("foo" -> "bar", "bar" -> "foo")

  def e1 = {
    val processor = spy(new RequestProcessor)
    doNothing
      .when(processor)
      .submit(
        any[BlockingQueue[CollectorRequest]](),
        any[ExecutionContext](),
        any[Option[Callback]](),
        any[EndpointParams](),
        any[CollectorRequest]()
      )

    val params  = EndpointParams("example.com", None, None)
    val emitter = new AsyncBatchEmitter(scala.concurrent.ExecutionContext.global, params, 3, None, processor)
    emitter.startWorker()

    emitter.send(payload)
    emitter.send(payload)

    Thread.sleep(100)
    there were noCallsTo(processor)
  }

  def e2 = {
    val processor = spy(new RequestProcessor)
    doNothing
      .when(processor)
      .submit(
        any[BlockingQueue[CollectorRequest]](),
        any[ExecutionContext](),
        any[Option[Callback]](),
        any[EndpointParams](),
        any[CollectorRequest]()
      )

    val params  = EndpointParams("example.com", None, None)
    val emitter = new AsyncBatchEmitter(scala.concurrent.ExecutionContext.global, params, 3, None, processor)
    emitter.startWorker()

    emitter.send(payload)
    emitter.send(payload)
    emitter.send(payload)

    eventually(
      there was one(processor).submit(
        any[BlockingQueue[CollectorRequest]](),
        any[ExecutionContext](),
        any[Option[Callback]](),
        any[EndpointParams](),
        any[CollectorRequest]()
      ))
  }

  def e3 = {
    val processor = spy(new RequestProcessor)
    doNothing
      .when(processor)
      .sendSync(any[ExecutionContext](),
                any[Duration](),
                any[EndpointParams](),
                any[CollectorRequest](),
                any[Option[Callback]]())

    val params  = EndpointParams("example.com", None, None)
    val emitter = new SyncBatchEmitter(params, 1.second, 3, None, processor)

    emitter.send(payload)
    emitter.send(payload)

    Thread.sleep(100)
    there were noCallsTo(processor)
  }

  def e4 = {
    val processor = spy(new RequestProcessor)
    doNothing
      .when(processor)
      .sendSync(any[ExecutionContext](),
                any[Duration](),
                any[EndpointParams](),
                any[CollectorRequest](),
                any[Option[Callback]]())

    val params  = EndpointParams("example.com", None, None)
    val emitter = new SyncBatchEmitter(params, 1.second, 3, None, processor)

    emitter.send(payload)
    emitter.send(payload)
    emitter.send(payload)

    eventually(
      there was one(processor).sendSync(any[ExecutionContext](),
                                        any[Duration](),
                                        any[EndpointParams](),
                                        any[CollectorRequest](),
                                        any[Option[Callback]]()))
  }
}
