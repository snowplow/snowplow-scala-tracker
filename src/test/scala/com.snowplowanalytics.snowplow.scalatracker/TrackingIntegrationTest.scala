package com.snowplowanalytics.snowplow.scalatracker

import akka.actor.{ ActorSystem }
import akka.stream.ActorFlowMaterializer
import com.typesafe.config.{ ConfigFactory }
import akka.testkit._
import scala.concurrent.duration._
import com.snowplowanalytics.snowplow.scalatracker.TestUtils
import com.snowplowanalytics.snowplow.scalatracker.SelfDescribingJson
import org.scalatest._
import scala.concurrent.Await
// json4s
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import Tracker._

class TrackingIntegrationTest(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("TrackingIntegrationTest", ConfigFactory.parseString("""
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

  import system.dispatcher
  implicit val materializer = ActorFlowMaterializer()

  "trackUnstructuredEvent" must {
    "send the server the payload with unstructured events" in {

      import emitters.Emitter._
      import emitters.Emitter
      import akka.http.scaladsl.server.directives._
      import akka.http.scaladsl.server.Directives._
      import akka.http.scaladsl.model._
      import akka.http.scaladsl.Http

      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()

      val testRoute = {
        get {
          path("/i") {
            complete("Hello")
          }
        }
      }

      val testNamespace = "mytracker"
      val testAppId = "myapp"
      implicit val attr = TrackerImpl.Attributes(namespace = testNamespace, appId = testAppId, encodeBase64 = false)
      // val context: Seq[SelfDescribingJson] = Nil
      implicit val ts = Some(0l)

      val testBinding = Http().bindAndHandle(testRoute, host, port)
      Await.result(testBinding, 1.second)

      var payloadFromServer: Payload = scala.collection.mutable.Map.empty

      val emitter = system.actorOf(Emitter.props(host, port))
      val tracker = new TrackerImpl(List(emitter))

      tracker.trackUnstructuredEvent(UnstructEvent(unstructEventJson))
    }
  }

}