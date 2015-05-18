package com.snowplowanalytics.snowplow.scalatracker
package emitters

// Scala
import scala.concurrent.{
  Future,
  Await
}
import scala.concurrent.duration._

// Akka
import akka.io.IO
import akka.actor.{Props, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout

// Spray
import spray.http._
import spray.httpx.{UnsuccessfulResponseException => UrUnsuccessfulResponseException}
import spray.httpx.unmarshalling.FromResponseUnmarshaller
import spray.util._
import spray.client.pipelining._
import spray.can.Http
import spray.can.Http.HostConnectorSetup
import spray.io.ClientSSLEngineProvider

object TEmitter {
  private val Encoding = "UTF-8"

  implicit val system = ActorSystem(generated.ProjectSettings.name)
  import system.dispatcher
  import system.log

  val longTimeout = 5.minutes
  implicit val timeout = Timeout(longTimeout)

  def attemptGet(host: String, payload: Map[String, String]): Boolean = {
    val connectedSendReceive = for {
      Http.HostConnectorInfo(connector, _) <-
        IO(Http) ? Http.HostConnectorSetup(host, sslEncryption = false)
    } yield sendReceive(connector)

    val pipeline = for (sendRecv <- connectedSendReceive) yield sendRecv

    val uri = Uri()
      .withScheme("http")
      .withPath(Uri.Path("/i"))
      .withAuthority(Uri.Authority(Uri.Host(host)))
      .withQuery(payload)

    val req = Get(uri)
    val future = pipeline.flatMap(_(req))
    val result = Await.ready(future, longTimeout).value.get

    result match {
      case scala.util.Success(s) => s.status.isSuccess
      case scala.util.Failure(f) => false
    }
  }

  def shutdown() {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown
  }
}

trait TEmitter {

  def httpGet(endpoint: String, evt: Map[String, String]) {
    
  }

  def blockingGet(event: Map[String, String], endpoint: String) {

  }

  def input(event: Map[String, String]): Unit
}
