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

// json4s
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

// Specs2
import org.specs2.mutable.Specification

class PayloadSpec extends Specification {

  "add" should {

    "add a new key-value pair to the payload" in {

      val payload = new Payload()

      payload.add("e", "se")
      payload.add("tna", "mytracker")

      payload.get must_== Map("e" -> "se", "tna" -> "mytracker")
    }
  }

  "addDict" should {

    "add a dictionary of key-value pairs to the payload" in {

      val payload = new Payload()

      payload.addDict(Map("e" -> "se", "tna" -> "mytracker"))

      payload.get must_== Map("e" -> "se", "tna" -> "mytracker")
    }
  }

  "addJson" should {

    "stringify a JSON and add it to the payload" in {

      val payload = new Payload()

      payload.addJson(("k" -> "v"), false, "enc", "plain")

      payload.get must_== Map("plain" -> """{"k":"v"}""")
    }

    "stringify and encode a JSON and add it to the payload" in {

      val payload = new Payload()

      payload.addJson(("k" -> "v"), true, "enc", "plain")

      payload.get must_== Map("enc" -> "eyJrIjoidiJ9")
    }
  }
}
