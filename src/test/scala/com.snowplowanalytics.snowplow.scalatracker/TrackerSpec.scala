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
// import org.specs2.mutable.Specification

// Scalatest
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll

// akka testkit
import akka.actor.{ ActorSystem, Actor }
import akka.testkit._
import akka.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory }
import scala.concurrent.duration._

class TrackerSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  import Tracker._

  def this() = this(ActorSystem("TrackerSpec", ConfigFactory.parseString("""
    akka.loglevel = INFO
    akka.log-dead-letters = off""")))

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

  "trackUnstructEvent" must {
    "send an unstructured event to the emitter" in {

      import akka.testkit.TestActorRef
      import emitters._
      import emitters.Emitter._

      val testNamespace = "mytracker"
      val testAppId = "myapp"

      implicit val attr = TrackerImpl.Attributes(namespace = testNamespace, appId = testAppId, encodeBase64 = false)
      // val context: Seq[SelfDescribingJson] = Nil
      implicit val ts = Some(0l)

      var payloadFromEmitter: Payload = scala.collection.mutable.Map.empty

      val emitterTest = TestActorRef(new Actor {
        def receive = {
          case payload: Payload =>
            payloadFromEmitter = payload
        }
      })

      val tracker = new TrackerImpl(List(emitterTest))

      tracker.trackUnstructuredEvent(UnstructEvent(unstructEventJson))

      assert(payloadFromEmitter("p") === "srv")
      assert(payloadFromEmitter("aid") === testAppId)
      assert(payloadFromEmitter("tna") === testNamespace)
      assert(payloadFromEmitter("e") === "ue")
      assert(payloadFromEmitter("ue_pr") === """{"schema":"iglu:com.snowplowanalytics.snowplow/unstruct_event/jsonschema/1-0-0","data":{"schema":"iglu:com.snowplowanalytics.snowplow/myevent/jsonschema/1-0-0","data":{"k1":"v1","k2":"v2"}}}""")
      // assert(payloadFromEmitter("tv") === s"scala-${generate.ProjectSettings.version}")
    }

    "allow adding Subject data to all event" in {

      import akka.testkit.TestActorRef
      import emitters._
      import emitters.Emitter._

      val testNamespace = "mytracker"
      val testAppId = "myapp"

      implicit val attr = TrackerImpl.Attributes(namespace = testNamespace, appId = testAppId, encodeBase64 = false)
      // implicit val context: Seq[SelfDescribingJson] = Nil
      implicit val ts = Some(0l)

      var payloadFromEmitter: Payload = scala.collection.mutable.Map.empty

      val emitterTest = TestActorRef(new Actor {
        def receive = {
          case payload: Payload =>
            payloadFromEmitter = payload
        }
      })

      val subject: Option[Subject] = Some(
        new Subject()
          .setPlatform(Mobile)
          .setUserId("sabnis")
          .setScreenResolution(200, 300)
          .setViewport(50, 100)
          .setColorDepth(24)
          .setTimezone("Europe London")
          .setDomainUserId("17")
          .setIpAddress("255.255.255.255")
          .setNetworkUserId("id"))

      val tracker = new TrackerImpl(List(emitterTest), subject)

      tracker.trackUnstructuredEvent(UnstructEvent(unstructEventJson))

      assert(payloadFromEmitter("p") === "mob")
      assert(payloadFromEmitter("uid") === "sabnis")
      assert(payloadFromEmitter("res") === "200x300")
      assert(payloadFromEmitter("vp") === "50x100")
      assert(payloadFromEmitter("cd") === "24")
      assert(payloadFromEmitter("tz") === "Europe London")
      assert(payloadFromEmitter("duid") === "17")
      assert(payloadFromEmitter("ip") === "255.255.255.255")
      assert(payloadFromEmitter("tnuid") === "id")
      assert(payloadFromEmitter("aid") === testAppId)
      assert(payloadFromEmitter("tna") === testNamespace)
      assert(payloadFromEmitter("e") === "ue")
      assert(payloadFromEmitter("ue_pr") === """{"schema":"iglu:com.snowplowanalytics.snowplow/unstruct_event/jsonschema/1-0-0","data":{"schema":"iglu:com.snowplowanalytics.snowplow/myevent/jsonschema/1-0-0","data":{"k1":"v1","k2":"v2"}}}""")
      // assert(payloadFromEmitter("tv") === s"scala-${generate.ProjectSettings.version}")
    }

    "allow adding custom contexts to event" in {

      import akka.testkit.TestActorRef
      import emitters._
      import emitters.Emitter._

      val testNamespace = "mytracker"
      val testAppId = "myapp"

      implicit val attr = TrackerImpl.Attributes(namespace = testNamespace, appId = testAppId, encodeBase64 = false)
      implicit val ts = Some(0l)

      var payloadFromEmitter: Payload = scala.collection.mutable.Map.empty
      val emitterTest = TestActorRef(new Actor {
        def receive = {
          case payload: Payload =>
            payloadFromEmitter = payload
        }
      })

      val tracker = new TrackerImpl(List(emitterTest))(attr)

      tracker.trackUnstructuredEvent(UnstructEvent(unstructEventJson), contexts)

      assert(payloadFromEmitter("co") === """{"schema":"iglu:com.snowplowanalytics.snowplow/contexts/jsonschema/1-0-0","data":[{"schema":"iglu:com.snowplowanalytics.snowplow/context1/jsonschema/1-0-0","data":{"number":20}},{"schema":"iglu:com.snowplowanalytics.snowplow/context1/jsonschema/1-0-0","data":{"letters":["a","b","c"]}}]}""")
    }
  }

  // "trackStructuredEvent" should {

  // }

  // "trackPageView" should {

  // }

  // "trackECommerceTransaction" should {

  // }
}
