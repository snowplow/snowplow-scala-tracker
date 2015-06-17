package com.snowplowanalytics.snowplow.scalatracker.emitters

import akka.actor.{ Actor, ActorSystem }
import akka.pattern.ask
import akka.testkit.{ TestActorRef, TestKit, ImplicitSender }
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
import com.typesafe.config.{ ConfigFactory, Config }

import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future

import akka.stream.ActorFlowMaterializer
import akka.http.scaladsl.model._
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import HttpMethods._
import com.snowplowanalytics.snowplow.scalatracker.TestUtils

class EmitterSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  val testConf: Config = ConfigFactory.parseString("""
    akka.loglevel = INFO
    akka.log-dead-letters = off""")

  import system.dispatcher
  implicit val materializer = ActorFlowMaterializer()

  implicit lazy val timeout = Timeout(5.seconds)

  def this() = this(ActorSystem("EmitterSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "An Emitter actor " must {
    "handle payload messages" in {
      import Emitter._
      import scala.concurrent.Await
      import scala.util.Success

      val (_, hostname, port) = TestUtils.temporaryServerHostnameAndPort()

      val testBinding = Http().bindAndHandleSync(_ => HttpResponse(), hostname, port)
      Await.result(testBinding, 1.second)

      val payload: Payload = scala.collection.mutable.Map.empty
      val emitter = system.actorOf(Emitter.props(hostname, port))

      val (pay, res: HttpResponse) = Await.result(emitter ? payload, 1.second)

      assert(payload === pay)
      assert(res.status === StatusCodes.OK)
    }
  }
}