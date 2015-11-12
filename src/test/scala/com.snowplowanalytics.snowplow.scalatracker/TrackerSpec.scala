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
import org.json4s.JsonDSL._

// Specs2
import org.specs2.mutable.Specification

import emitters.TEmitter

class TrackerSpec extends Specification {

  class TestEmitter extends TEmitter {

    var lastInput = Map[String, String]()

    def input(event: Map[String, String]) {
      lastInput = event
    }
  }

  val unstructEventJson = SelfDescribingJson(
    "iglu:com.snowplowanalytics.snowplow/myevent/jsonschema/1-0-0",
    ("k1" -> "v1") ~ ("k2" -> "v2"))

  val contexts = List(
    SelfDescribingJson(
      "iglu:com.snowplowanalytics.snowplow/context1/jsonschema/1-0-0",
      ("number" -> 20)),
    SelfDescribingJson(
    "iglu:com.snowplowanalytics.snowplow/context1/jsonschema/1-0-0",
    ("letters" -> List("a", "b", "c"))))

  "trackUnstructEvent" should {

    "send an unstructured event to the emitter" in {

      val emitter = new TestEmitter

      val tracker = new Tracker(List(emitter), "mytracker", "myapp", false)

      tracker.trackUnstructEvent(unstructEventJson)

      val event = emitter.lastInput

      """[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}""".r.unapplySeq(event("eid")) must beSome

      """\d*""".r.unapplySeq(event("dtm")) must beSome

      event("tv") must_== s"scala-${generated.ProjectSettings.version}"
      event("p") must_== "srv"
      event("e") must_== "ue"
      event("aid") must_== "myapp"
      event("tna") must_== "mytracker"

      event("ue_pr") must_== """{"schema":"iglu:com.snowplowanalytics.snowplow/unstruct_event/jsonschema/1-0-0","data":{"schema":"iglu:com.snowplowanalytics.snowplow/myevent/jsonschema/1-0-0","data":{"k1":"v1","k2":"v2"}}}"""

    }
  }

  "setSubject" should {

    "add the Subject's data to all events" in {

      val emitter = new TestEmitter

      val tracker = new Tracker(List(emitter), "mytracker", "myapp", false)

      val subject = new Subject()
        .setPlatform(Mobile)
        .setUserId("sabnis")
        .setScreenResolution(200, 300)
        .setViewport(50,100)
        .setColorDepth(24)
        .setTimezone("Europe London")
        .setLang("en")
        .setDomainUserId("17")
        .setIpAddress("255.255.255.255")
        .setUseragent("Mozilla/5.0 (Windows NT 5.1; rv:24.0) Gecko/20100101 Firefox/24.0")
        .setNetworkUserId("id")

      tracker.setSubject(subject)

      tracker.trackUnstructEvent(unstructEventJson)

      val event = emitter.lastInput

      event("p") must_== "mob"
      event("uid") must_== "sabnis"
      event("res") must_== "200x300"
      event("vp") must_== "50x100"
      event("cd") must_== "24"
      event("tz") must_== "Europe London"
      event("lang") must_== "en"
      event("duid") must_== "17"
      event("ip") must_== "255.255.255.255"
      event("ua") must_== "Mozilla/5.0 (Windows NT 5.1; rv:24.0) Gecko/20100101 Firefox/24.0"
      event("tnuid") must_== "id"

    }
  }

  "track" should {

    "add custom contexts to the event" in {

      val emitter = new TestEmitter

      val tracker = new Tracker(List(emitter), "mytracker", "myapp", false)

      tracker.trackUnstructEvent(unstructEventJson, contexts)

      val event = emitter.lastInput

      event("co") must_== """{"schema":"iglu:com.snowplowanalytics.snowplow/contexts/jsonschema/1-0-1","data":[{"schema":"iglu:com.snowplowanalytics.snowplow/context1/jsonschema/1-0-0","data":{"number":20}},{"schema":"iglu:com.snowplowanalytics.snowplow/context1/jsonschema/1-0-0","data":{"letters":["a","b","c"]}}]}"""

    }
  }
}
