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

import java.util.concurrent.atomic.AtomicInteger

import scalaj.http.{HttpRequest, HttpResponse}
import scala.concurrent.ExecutionContext.Implicits.global

import com.snowplowanalytics.snowplow.scalatracker.{Emitter, Payload}
import com.snowplowanalytics.snowplow.scalatracker.Emitter.{EventQueuePolicy, RetryPolicy}

import org.specs2.Specification

class BatchEmitterSpec extends Specification {

  override def is = s2"""

    AsyncEmitter's buffer should not flush before reaching buffer's event cardinality limit $e1
    AsyncEmitter's buffer should flush after reaching buffer's event cardinality limit      $e2
    AsyncEmitter's buffer should not flush before reaching buffer's payload size limit      $e3
    AsyncEmitter's buffer should flush after reaching buffer's payload size limit           $e4
    AsyncEmitter's buffer should flush unsent events after closing                          $e5
    SyncEmitter's buffer should not flush before reaching buffer's event cardinality limit  $e6
    SyncEmitter's buffer should flush after reaching buffer's event cardinality limit       $e7
    SyncEmitter's buffer should not flush before reaching buffer's payload size limit       $e8
    SyncEmitter's buffer should flush after reaching buffer's payload size limit            $e9
    SyncEmitter's buffer should flush when the flush method is called                       $e10

  """

  val payload = Payload(Map("foo" -> "bar", "bar" -> "foo"))

  def withAsyncEmitter[R](e: AsyncEmitter)(f: AsyncEmitter => R): R =
    try {
      e.startWorker()
      f(e)
    } finally {
      e.close()
    }

  def e1 = {
    val counter = new AtomicInteger(0)
    def processor(request: HttpRequest): HttpResponse[Array[Byte]] = {
      counter.getAndIncrement
      new HttpResponse(Array(), 200, Map())
    }

    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.EventsCardinality(3)
    withAsyncEmitter(
      new AsyncEmitter(params, bufferConfig, None, RetryPolicy.Default, EventQueuePolicy.Default, processor, Nil, 10)) {
      emitter =>
        emitter.send(payload)
        emitter.send(payload)

        Thread.sleep(100)
        counter.get must_== 0
    }
  }

  def e2 = {
    val counter = new AtomicInteger(0)
    def processor(request: HttpRequest): HttpResponse[Array[Byte]] = {
      counter.getAndIncrement
      new HttpResponse(Array(), 200, Map())
    }

    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.EventsCardinality(3)
    withAsyncEmitter(
      new AsyncEmitter(params, bufferConfig, None, RetryPolicy.Default, EventQueuePolicy.Default, processor, Nil, 10)) {
      emitter =>
        emitter.send(payload)
        emitter.send(payload)
        emitter.send(payload)

        eventually(counter.get must_== 1)
    }
  }

  def e3 = {
    val counter = new AtomicInteger(0)
    def processor(request: HttpRequest): HttpResponse[Array[Byte]] = {
      counter.getAndIncrement
      new HttpResponse(Array(), 200, Map())
    }

    val maxBytes     = Payload.postPayload(Seq(payload, payload, payload)).getBytes.length
    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.PayloadSize(maxBytes)
    withAsyncEmitter(
      new AsyncEmitter(params, bufferConfig, None, RetryPolicy.Default, EventQueuePolicy.Default, processor, Nil, 10)) {
      emitter =>
        emitter.send(payload)
        emitter.send(payload)

        Thread.sleep(100)
        counter.get must_== 0
    }
  }
  def e4 = {
    val counter = new AtomicInteger(0)
    def processor(request: HttpRequest): HttpResponse[Array[Byte]] = {
      counter.getAndIncrement
      new HttpResponse(Array(), 200, Map())
    }

    val maxBytes     = Payload.postPayload(Seq(payload, payload, payload)).getBytes.length
    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.PayloadSize(maxBytes)
    withAsyncEmitter(
      new AsyncEmitter(params, bufferConfig, None, RetryPolicy.Default, EventQueuePolicy.Default, processor, Nil, 10)) {
      emitter =>
        emitter.send(payload)
        emitter.send(payload)
        emitter.send(payload)
        eventually(counter.get must_== 1)
    }
  }

  def e5 = {
    val counter = new AtomicInteger(0)
    def processor(request: HttpRequest): HttpResponse[Array[Byte]] = {
      counter.getAndIncrement
      new HttpResponse(Array(), 200, Map())
    }

    val maxBytes     = Payload.postPayload(Seq(payload, payload, payload)).getBytes.length
    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.PayloadSize(maxBytes)
    withAsyncEmitter(
      new AsyncEmitter(params, bufferConfig, None, RetryPolicy.Default, EventQueuePolicy.Default, processor, Nil, 10)) {
      emitter =>
        emitter.send(payload)
    }
    Thread.sleep(100)
    eventually(counter.get must_== 1)
  }

  def e6 = {
    val counter = new AtomicInteger(0)
    def processor(request: HttpRequest): HttpResponse[Array[Byte]] = {
      counter.getAndIncrement
      new HttpResponse(Array(), 200, Map())
    }

    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.EventsCardinality(3)
    val emitter      = new SyncEmitter(params, bufferConfig, None, RetryPolicy.Default, processor, Nil)

    emitter.send(payload)
    emitter.send(payload)

    counter.get must_== 0
  }

  def e7 = {
    val counter = new AtomicInteger(0)
    def processor(request: HttpRequest): HttpResponse[Array[Byte]] = {
      counter.getAndIncrement
      new HttpResponse(Array(), 200, Map())
    }

    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.EventsCardinality(3)
    val emitter      = new SyncEmitter(params, bufferConfig, None, RetryPolicy.Default, processor, Nil)

    emitter.send(payload)
    emitter.send(payload)
    emitter.send(payload)

    counter.get must_== 1
  }

  def e8 = {
    val counter = new AtomicInteger(0)
    def processor(request: HttpRequest): HttpResponse[Array[Byte]] = {
      counter.getAndIncrement
      new HttpResponse(Array(), 200, Map())
    }

    val maxBytes     = Payload.postPayload(Seq(payload, payload, payload)).getBytes.length
    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.PayloadSize(maxBytes)
    val emitter      = new SyncEmitter(params, bufferConfig, None, RetryPolicy.Default, processor, Nil)

    emitter.send(payload)
    emitter.send(payload)

    counter.get must_== 0
  }

  def e9 = {
    val counter = new AtomicInteger(0)
    def processor(request: HttpRequest): HttpResponse[Array[Byte]] = {
      counter.getAndIncrement
      new HttpResponse(Array(), 200, Map())
    }

    val maxBytes     = Payload.postPayload(Seq(payload, payload, payload)).getBytes.length
    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.PayloadSize(maxBytes)
    val emitter      = new SyncEmitter(params, bufferConfig, None, RetryPolicy.Default, processor, Nil)

    emitter.send(payload)
    emitter.send(payload)
    emitter.send(payload)
    emitter.send(payload)

    counter.get must_== 1
  }

  def e10 = {
    val counter = new AtomicInteger(0)
    def processor(request: HttpRequest): HttpResponse[Array[Byte]] = {
      counter.getAndIncrement
      new HttpResponse(Array(), 200, Map())
    }

    val maxBytes     = Payload.postPayload(Seq(payload, payload, payload)).getBytes.length
    val params       = Emitter.EndpointParams("example.com", None, None)
    val bufferConfig = Emitter.BufferConfig.PayloadSize(maxBytes)
    val emitter      = new SyncEmitter(params, bufferConfig, None, RetryPolicy.Default, processor, Nil)

    emitter.send(payload)
    emitter.flush()

    counter.get must_== 1
  }
}
