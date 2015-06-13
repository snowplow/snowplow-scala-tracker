package com.snowplowanalytics.snowplow.scalatracker.emitters

import akka.actor.{ Actor, ActorSystem }
import akka.testkit.{ TestActorRef, TestKit, ImplicitSender }
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
// import com.snowplowanalytics.snowplow.scalatracker.emitters.Emitter

class EmitterSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  val host = "127.0.0.1"
  val port = 8087

  def this() = this(ActorSystem("EmitterSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "An Emitter actor " must {
    "handle payload messages" in {
      import Emitter._
      val payload: Payload = scala.collection.mutable.Map.empty
      val emitter = system.actorOf(Emitter.props(host, port))

      emitter ! payload
      expectMsg(payload)
    }
  }
}