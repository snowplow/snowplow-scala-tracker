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
package com.snowplowanalytics.snowplow.scalatracker.emitters.id

import java.util.concurrent.BlockingQueue

import scala.concurrent.duration._

import cats.Id

import com.snowplowanalytics.snowplow.scalatracker.Emitter

import org.specs2.Specification
import org.specs2.mock.Mockito

import scala.concurrent.ExecutionContext

class BatchEmitterSpec extends Specification with Mockito {

  override def is = s2"""

    AsyncEmitter's buffer should not flush before reaching buffer's event cardinality limit $e1
    AsyncEmitter's buffer should flush after reaching buffer's event cardinality limit      $e2
    AsyncEmitter's buffer should not flush before reaching buffer's payload size limit      $e3
    AsyncEmitter's buffer should flush after reaching buffer's payload size limit           $e4
    SyncEmitter's buffer should not flush before reaching buffer's event cardinality limit  $e5
    SyncEmitter's buffer should flush after reaching buffer's event cardinality limit       $e6
    SyncEmitter's buffer should not flush before reaching buffer's payload size limit       $e7
    SyncEmitter's buffer should flush after reaching buffer's payload size limit            $e8

  """

  val payload = Map("foo" -> "bar", "bar" -> "foo")

  def e1 = {
    val processor = spy(new RequestProcessor)
    doNothing
      .when(processor)
      .submit(
        any[BlockingQueue[Emitter.Request]](),
        any[ExecutionContext](),
        any[Option[Emitter.Callback[Id]]](),
        any[Emitter.EndpointParams](),
        any[Emitter.Request]()
      )

    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.EventsCardinality(3)
    val emitter      = new AsyncEmitter(scala.concurrent.ExecutionContext.global, params, bufferConfig, None, processor)
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
        any[BlockingQueue[Emitter.Request]](),
        any[ExecutionContext](),
        any[Option[Emitter.Callback[Id]]](),
        any[Emitter.EndpointParams](),
        any[Emitter.Request]()
      )

    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.EventsCardinality(3)
    val emitter      = new AsyncEmitter(scala.concurrent.ExecutionContext.global, params, bufferConfig, None, processor)
    emitter.startWorker()

    emitter.send(payload)
    emitter.send(payload)
    emitter.send(payload)

    eventually(
      there was one(processor).submit(
        any[BlockingQueue[Emitter.Request]](),
        any[ExecutionContext](),
        any[Option[Emitter.Callback[Id]]](),
        any[Emitter.EndpointParams](),
        any[Emitter.Request]()
      ))
  }

  def e3 = {
    val processor = spy(new RequestProcessor)
    doNothing
      .when(processor)
      .submit(
        any[BlockingQueue[Emitter.Request]](),
        any[ExecutionContext](),
        any[Option[Emitter.Callback[Id]]](),
        any[Emitter.EndpointParams](),
        any[Emitter.Request]()
      )

    val payloadSize  = Emitter.payloadSize(payload)
    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.PayloadSize(payloadSize * 3)
    val emitter      = new AsyncEmitter(scala.concurrent.ExecutionContext.global, params, bufferConfig, None, processor)
    emitter.startWorker()

    emitter.send(payload)
    emitter.send(payload)

    Thread.sleep(100)
    there were noCallsTo(processor)
  }

  def e4 = {
    val processor = spy(new RequestProcessor)
    doNothing
      .when(processor)
      .submit(
        any[BlockingQueue[Emitter.Request]](),
        any[ExecutionContext](),
        any[Option[Emitter.Callback[Id]]](),
        any[Emitter.EndpointParams](),
        any[Emitter.Request]()
      )

    val payloadSize  = Emitter.payloadSize(payload)
    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.PayloadSize(payloadSize * 3)
    val emitter      = new AsyncEmitter(scala.concurrent.ExecutionContext.global, params, bufferConfig, None, processor)
    emitter.startWorker()

    emitter.send(payload)
    emitter.send(payload)
    emitter.send(payload)
    emitter.send(payload)

    Thread.sleep(100)
    eventually(
      there was one(processor).submit(
        any[BlockingQueue[Emitter.Request]](),
        any[ExecutionContext](),
        any[Option[Emitter.Callback[Id]]](),
        any[Emitter.EndpointParams](),
        any[Emitter.Request]()
      ))
  }

  def e5 = {
    val processor = spy(new RequestProcessor)
    doNothing
      .when(processor)
      .sendSync(any[ExecutionContext](),
                any[Duration](),
                any[Emitter.EndpointParams](),
                any[Emitter.Request](),
                any[Option[Emitter.Callback[Id]]]())

    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.EventsCardinality(3)
    val emitter      = new SyncEmitter(params, 1.second, bufferConfig, None, processor)

    emitter.send(payload)
    emitter.send(payload)

    Thread.sleep(100)
    there were noCallsTo(processor)
  }

  def e6 = {
    val processor = spy(new RequestProcessor)
    doNothing
      .when(processor)
      .sendSync(any[ExecutionContext](),
                any[Duration](),
                any[Emitter.EndpointParams](),
                any[Emitter.Request](),
                any[Option[Emitter.Callback[Id]]]())

    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.EventsCardinality(3)
    val emitter      = new SyncEmitter(params, 1.second, bufferConfig, None, processor)

    emitter.send(payload)
    emitter.send(payload)
    emitter.send(payload)

    eventually(
      there was one(processor).sendSync(any[ExecutionContext](),
                                        any[Duration](),
                                        any[Emitter.EndpointParams](),
                                        any[Emitter.Request](),
                                        any[Option[Emitter.Callback[Id]]]()))
  }

  def e7 = {
    val processor = spy(new RequestProcessor)
    doNothing
      .when(processor)
      .sendSync(any[ExecutionContext](),
                any[Duration](),
                any[Emitter.EndpointParams](),
                any[Emitter.Request](),
                any[Option[Emitter.Callback[Id]]]())

    val payloadSize  = Emitter.payloadSize(payload)
    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.PayloadSize(payloadSize * 3)
    val emitter      = new SyncEmitter(params, 1.second, bufferConfig, None, processor)

    emitter.send(payload)
    emitter.send(payload)

    Thread.sleep(100)
    there were noCallsTo(processor)
  }

  def e8 = {
    val processor = spy(new RequestProcessor)
    doNothing
      .when(processor)
      .sendSync(any[ExecutionContext](),
                any[Duration](),
                any[Emitter.EndpointParams](),
                any[Emitter.Request](),
                any[Option[Emitter.Callback[Id]]]())

    val payloadSize  = Emitter.payloadSize(payload)
    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.PayloadSize(payloadSize * 3)
    val emitter      = new SyncEmitter(params, 1.second, bufferConfig, None, processor)

    emitter.send(payload)
    emitter.send(payload)
    emitter.send(payload)
    emitter.send(payload)

    Thread.sleep(100)
    eventually(
      there was one(processor).sendSync(any[ExecutionContext](),
                                        any[Duration](),
                                        any[Emitter.EndpointParams](),
                                        any[Emitter.Request](),
                                        any[Option[Emitter.Callback[Id]]]()))
  }
}
