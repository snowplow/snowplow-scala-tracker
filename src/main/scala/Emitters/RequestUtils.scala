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

// Config
import com.typesafe.config.ConfigFactory

object RequestUtils {
  private val Encoding = "UTF-8"

  implicit val system = ActorSystem(
    generated.ProjectSettings.name,
    ConfigFactory.parseString("akka.daemonic=on"))

  import system.dispatcher
  import system.log

  val longTimeout = 5.minutes
  implicit val timeout = Timeout(longTimeout)

  // Close all connections when the application exits
  Runtime.getRuntime().addShutdownHook(new Thread() {
    override def run() {
      shutdown()
    }
  })

  def attemptGet(host: String, payload: Map[String, String], port: Int = 80): Boolean = {
    val connectedSendReceive = for {
      Http.HostConnectorInfo(connector, _) <-
        IO(Http) ? Http.HostConnectorSetup(host, port = port, sslEncryption = false)
    } yield sendReceive(connector)

    val pipeline = for (sendRecv <- connectedSendReceive) yield sendRecv

    val uri = Uri()
      .withScheme("http")
      .withPath(Uri.Path("/i"))
      .withAuthority(Uri.Authority(Uri.Host(host), port))
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
