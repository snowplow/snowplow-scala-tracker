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

import org.specs2.mutable.Specification

class BufferSpec extends Specification {
  "An event buffer" should {

    "correctly report when it is full" in {

      val payload = Payload().add("e", "se").add("tna", "mytracker")

      val maxBytes = Payload.postPayload(Seq(payload, payload, payload)).getBytes.length

      // A config that is full after 3 payloads
      val config1 = Emitter.BufferConfig.PayloadSize(maxBytes)

      // A config that has remaining capacity after 3 payloads
      val config2 = Emitter.BufferConfig.PayloadSize(maxBytes + 1)

      val buffer1 = Buffer(config1).add(payload).add(payload).add(payload)
      val buffer2 = Buffer(config2).add(payload).add(payload).add(payload)

      buffer1.isFull must beTrue
      buffer2.isFull must beFalse

    }
  }
}
