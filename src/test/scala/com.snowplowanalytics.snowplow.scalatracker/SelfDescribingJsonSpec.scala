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

// Specs2
import org.specs2.mutable.Specification

class SelfDescribingJsonSpec extends Specification {

  "Passing an unstructured event" should {

    "create an outer unstructured event JSON" in {

      val actual = SelfDescribingJson(
        "iglu:com.snowplowanalytics.snowplow/unstruct_event/jsonschema/1-0-0",
        SelfDescribingJson(
          "iglu:com.snowplowanalytics.snowplow/my_event/jsonschema/1-0-0",
          ("k1" -> "v1") ~ ("k2" -> "v2")))

      actual.toJObject must_== 
      ("schema" -> "iglu:com.snowplowanalytics.snowplow/unstruct_event/jsonschema/1-0-0") ~
      ("data" -> (
        ("schema" -> "iglu:com.snowplowanalytics.snowplow/my_event/jsonschema/1-0-0") ~
        ("data" -> (
          ("k1" -> "v1") ~ ("k2" -> "v2")))))
    }
  }

  "Passing a list of custom contexts" should {

    "create an outer contexts JSON" in {

      val actual = SelfDescribingJson(
        "iglu:com.snowplowanalytics.snowplow/contexts/jsonschema/1-0-0",
        List(
          SelfDescribingJson(
            "iglu:com.snowplowanalytics.snowplow/my_context/jsonschema/1-0-0",
            ("k1" -> "v1") ~ ("k2" -> "v2"))))

      actual.toJObject must_== 
      ("schema" -> "iglu:com.snowplowanalytics.snowplow/contexts/jsonschema/1-0-0") ~
      ("data" -> List(
        ("schema" -> "iglu:com.snowplowanalytics.snowplow/my_context/jsonschema/1-0-0") ~
        ("data" -> (
          ("k1" -> "v1") ~ ("k2" -> "v2")))))
    }
  }

  "Passing one argument for each part of the schema" should {

    "construct a SelfDescribingJson correctly" in {

      val actual = SelfDescribingJson(
        "iglu",
        "com.snowplowanalytics.snowplow",
        "heartbeat",
        "jsonschema",
        1,
        0,
        0,
        ("interval" -> 1000))

      actual.toJObject must_==
        ("schema" -> "iglu:com.snowplowanalytics.snowplow/heartbeat/jsonschema/1-0-0") ~
        ("data" -> (
          "interval" -> 1000))

    }

  }
}
