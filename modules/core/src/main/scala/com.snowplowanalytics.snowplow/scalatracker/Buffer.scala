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

/** Represents an emitter's internal state as it buffers events.
 *
 *  Emitters typically send events in larger batches, depending on the buffering configuration
 */
private[scalatracker] trait Buffer {

  import Buffer._

  /* Resets the internal state to an empty buffer, i.e. after sending the pending batch in a post request
   */
  def reset: Buffer

  /** Update the buffered state with a new payload
   */
  def add(payload: Payload): Buffer

  /** Is the buffer full - i.e. is it time to send the buffered batch to the collector
   */
  def isFull: Boolean

  /** Convert the pending batch to a Request.
   *
   *  Can return None of the batch was empty
   */
  def toRequest: Option[Request]

  /** Handle an event to update the state and possibly create a request which should be sent to the collector
   */
  def handle(action: Action): (Buffer, Option[Request]) =
    action match {
      case Action.Terminate | Action.Flush =>
        reset -> toRequest
      case Action.Enqueue(payload) =>
        val next = add(payload)
        if (next.isFull)
          reset -> next.toRequest
        else
          next -> None
    }
}

private[scalatracker] object Buffer {

  def apply(config: BufferConfig): Buffer =
    BufferImpl(Nil, 0, 0, config)

  private case class BufferImpl(toList: List[Payload], count: Int, bytes: Int, config: BufferConfig) extends Buffer {

    override def reset: Buffer = BufferImpl(Nil, 0, 0, config)

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

  /** ADT of actions the emitter needs to handle
   */
  sealed trait Action
  object Action {
    case class Enqueue(payload: Payload) extends Action
    case object Flush extends Action
    case object Terminate extends Action
  }
}
