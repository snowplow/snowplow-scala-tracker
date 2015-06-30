/*
 * Copyright (c) 2015 Snowplow Analytics Ltd. All rights reserved.
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
import scala.collection.mutable.Map

// Specs2
import org.specs2.mutable.Specification

class PayloadSpec extends Specification {
  import com.snowplowanalytics.snowplow.scalatracker.emitters.Emitter._

  "add" should {

    "add a new key-value pair to the payload" in {

      val payload: Payload = Map("e" -> "se")

      payload += ("e" -> "se")
      payload += ("tna" -> "mytracker")

      payload must_== Map("e" -> "se", "tna" -> "mytracker")
    }
  }

  "addDict" should {

    "add a dictionary of key-value pairs to the payload" in {

      val payload: Payload = Map.empty
      payload ++= Map("e" -> "se", "tna" -> "mytracker")

      payload must_== Map("e" -> "se", "tna" -> "mytracker")
    }
  }

  "addJson" should {

    "stringify a JSON and add it to the payload" in {

      val payload: Payload = Map.empty
      val jsonString = compact(render(("k" -> "v")))

      payload.addJson(jsonString, false, which = ("enc", "plain"))

      payload must_== Map("plain" -> """{"k":"v"}""")
    }

    "stringify and encode a JSON and add it to the payload" in {

      val payload: Payload = Map.empty
      val jsonString = compact(render(("k" -> "v")))

      payload.addJson(jsonString, true, which = ("enc", "plain"))

      payload must_== Map("enc" -> "eyJrIjoidiJ9")
    }
  }
}
