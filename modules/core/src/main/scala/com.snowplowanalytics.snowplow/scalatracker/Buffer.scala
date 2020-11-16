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
package com.snowplowanalytics.snowplow.scalatracker

import io.circe.syntax._
import Emitter.{BufferConfig, Request}

trait Buffer {

  def add(payload: Payload): Buffer

  def isFull: Boolean

  def toRequest: Option[Request]
}

object Buffer {

  def apply(config: BufferConfig): Buffer =
    BufferImpl(Nil, 0, 0, config)

  private case class BufferImpl(toList: List[Payload], count: Int, bytes: Int, config: BufferConfig) extends Buffer {

    override def add(payload: Payload): Buffer = {
      val newBytes =
        if (toList.isEmpty) {
          Payload.postPayload(Seq(payload)).getBytes.length
        } else {
          payload.asJson.noSpaces.getBytes.length + bytes + 1
        }
      BufferImpl(payload :: toList, count + 1, newBytes, config)
    }

    override def isFull: Boolean =
      config match {
        case BufferConfig.NoBuffering =>
          toList.nonEmpty
        case BufferConfig.EventsCardinality(max) =>
          count >= max && count > 0
        case BufferConfig.PayloadSize(max) =>
          bytes >= max && bytes > 0
      }

    override def toRequest: Option[Request] =
      toList match {
        case Nil => None
        case single :: Nil if config == BufferConfig.NoBuffering =>
          Some(Request(single))
        case more =>
          Some(Request(more))
      }
  }
}
