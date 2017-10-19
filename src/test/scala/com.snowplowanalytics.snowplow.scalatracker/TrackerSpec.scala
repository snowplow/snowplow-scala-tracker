/*
 * Copyright (c) 2012-2016 Snowplow Analytics Ltd. All rights reserved.
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

    "implicitly (and without additional imports) assume device_timestamp when no data constructor specified for timestamp" in {

      val emitter = new TestEmitter

      val tracker = new Tracker(List(emitter), "mytracker", "myapp", false)

      tracker.trackStructEvent("e-commerce", "buy", property=Some("book"), timestamp=Some(1459778142000L)) // Long

      val event = emitter.lastInput

      (event("dtm") must_== "1459778142000").and(
        event.get("ttm") must beNone
      )

    }

    "set true_timestamp when data constructor applied explicitly" in {

      val emitter = new TestEmitter

      val tracker = new Tracker(List(emitter), "mytracker", "myapp", false)

      val timestamp = Tracker.TrueTimestamp(1459778542000L)

      tracker.trackStructEvent("e-commerce", "buy", property=Some("book"), timestamp=Some(timestamp))

      val event = emitter.lastInput

      (event("ttm") must_== "1459778542000").and(
        event.get("dtm") must beNone
      )

    }

  }

  "trackAddToCart" should {

    "add add_to_cart context to the event" in {
      val emitter = new TestEmitter

      val tracker = new Tracker(List(emitter), "mytracker", "myapp", false)

      tracker.trackAddToCart("aSku", Some("productName"), Some("category"), Some(99.99), 1, Some("USD"))

      val event = emitter.lastInput

      event("e") must_== "ue"
      event("ue_pr") must_== """{"schema":"iglu:com.snowplowanalytics.snowplow/unstruct_event/jsonschema/1-0-0","data":{"schema":"iglu:com.snowplowanalytics.snowplow/add_to_cart/jsonschema/1-0-0","data":{"sku":"aSku","name":"productName","category":"category","unitPrice":99.99,"quantity":1,"currency":"USD"}}}"""
    }
  }

  "trackRemoveFromCart" should {

    "add remove_from_cart context to the event" in {
      val emitter = new TestEmitter

      val tracker = new Tracker(List(emitter), "mytracker", "myapp", false)

      tracker.trackRemoveFromCart("aSku", Some("productName"), Some("category"), Some(99.99), 1, Some("USD"))

      val event = emitter.lastInput

      event("e") must_== "ue"
      event("ue_pr") must_== """{"schema":"iglu:com.snowplowanalytics.snowplow/unstruct_event/jsonschema/1-0-0","data":{"schema":"iglu:com.snowplowanalytics.snowplow/remove_from_cart/jsonschema/1-0-0","data":{"sku":"aSku","name":"productName","category":"category","unitPrice":99.99,"quantity":1.0,"currency":"USD"}}}"""
    }
  }

  "trackTransaction" should {

    "set the transaction parameters accordingly" in {
      val emitter = new TestEmitter

      val tracker = new Tracker(List(emitter), "mytracker", "myapp", false)

      tracker.trackTransaction("orderId", Some("affiliation"), 99.99, Some(7.99), Some(5.99), Some("city"), Some("state"), Some("country"), Some("USD") )

      val event = emitter.lastInput

      event("e") must_== "tr"
      event("tr_id") must_== "orderId"
      event("tr_af") must_== "affiliation"
      event("tr_tt") must_== "99.99"
      event("tr_tx") must_== "7.99"
      event("tr_sh") must_== "5.99"
      event("tr_ci") must_== "city"
      event("tr_st") must_== "state"
      event("tr_co") must_== "country"
      event("tr_cu") must_== "USD"
    }
  }

  "trackTransactionItem" should {

    "set the transaction item parameters accordingly" in {
      val emitter = new TestEmitter

      val tracker = new Tracker(List(emitter), "mytracker", "myapp", false)

      tracker.trackTransactionItem("orderId", "sku", Some("name"), Some("category"), 19.99, 5, Some("USD"))

      val event = emitter.lastInput

      event("e") must_== "ti"
      event("ti_id") must_== "orderId"
      event("ti_sk") must_== "sku"
      event("ti_nm") must_== "name"
      event("ti_ca") must_== "category"
      event("ti_pr") must_== "19.99"
      event("ti_qu") must_== "5"
      event("ti_cu") must_== "USD"
    }
  }

}
