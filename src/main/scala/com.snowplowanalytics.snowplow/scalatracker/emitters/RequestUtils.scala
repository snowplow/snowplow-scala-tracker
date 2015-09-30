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
import scala.util.{ Failure, Success }
import scala.util.control.NonFatal
import scala.concurrent.{
  Future,
  Await
}
import scala.concurrent.duration._

// Akka
import akka.io.IO
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout

// Spray
import spray.http._
import spray.httpx.marshalling.Marshaller
import spray.client.pipelining._
import spray.can.Http
import spray.util.{ Utils => _, _ }

// json4s
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

// Config
import com.typesafe.config.ConfigFactory

/**
 * Object to hold methods for sending HTTP requests
 */
object RequestUtils {
  // JSON object with Iglu URI to Schema for payload
  private val payloadBatchStub: JObject = ("schema", "iglu:com.snowplowanalytics.snowplow/payload_data/jsonschema/1-0-3")

  /**
   * Transform List of Map[String, String] to JSON array of objects
   *
   * @param payload list of string-to-string maps taken from HTTP query
   * @return JSON array represented as String
   */
  private def postPayload(payload: Seq[Map[String, String]]): String =
    compact(payloadBatchStub ~ ("data", payload))

  /**
   * Marshall batch of events to string payload with application/json
   */
  implicit val eventsMarshaller = Marshaller.of[Seq[Map[String,String]]](ContentTypes.`application/json`) {
    (value, ct, ctx) => ctx.marshalTo(HttpEntity(ct, postPayload(value)))
  }

  implicit val system = ActorSystem(
    generated.ProjectSettings.name,
    ConfigFactory.parseString("akka.daemonic=on"))
  import system.dispatcher                    // Context for Futures
  val longTimeout = 5.minutes
  implicit val timeout = Timeout(longTimeout)
  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

  // Close all connections when the application exits
  Runtime.getRuntime().addShutdownHook(new Thread() {
    override def run() {
      shutdown()
    }
  })

  /**
   * Construct GET request with single event payload
   *
   * @param host URL host (not header)
   * @param payload map of event keys
   * @param port URL port (not header)
   * @return HTTP request with event
   */
  private[emitters] def constructGetRequest(host: String, payload: Map[String, String], port: Int): HttpRequest = {
    val uri = Uri()
      .withScheme("http")
      .withPath(Uri.Path("/i"))
      .withAuthority(Uri.Authority(Uri.Host(host), port))
      .withQuery(payload)
    Get(uri)
  }

  /**
   * Construct POST request with batch event payload
   *
   * @param host URL host (not header)
   * @param payload list of events
   * @param port URL port (not header)
   * @return HTTP request with event
   */
  private[emitters] def constructPostRequest(host: String, payload: Seq[Map[String, String]], port: Int): HttpRequest = {
    val uri = Uri()
      .withScheme("http")
      .withPath(Uri.Path("/com.snowplowanalytics.snowplow/tp2"))
      .withAuthority(Uri.Authority(Uri.Host(host), port))
    Post(uri, payload)
  }

  /**
   * Attempt a GET request once
   *
   * @param host collector host
   * @param payload event map
   * @param port collector port
   * @return Whether the request succeeded
   */
  def attemptGet(host: String, payload: Map[String, String], port: Int = 80): Boolean = {
    val payloadWithStm = payload ++ Map("stm" -> System.currentTimeMillis().toString)
    val req = constructGetRequest(host, payloadWithStm, port)
    val future = pipeline(req)
    val result = Await.ready(future, longTimeout).value.get
    result match {
      case Success(s) => s.status.isSuccess   // 404 match Success(_) too
      case Failure(_) => false
    }
  }

  /**
   * Attempt a GET request until successful
   *
   * @param host collector host
   * @param payload event map
   * @param port collector port
   * @param backoffPeriod How long to wait between failed requests
   */
  def retryGetUntilSuccessful(host: String, payload: Map[String, String], port: Int = 80, backoffPeriod: Long) {
    val getSuccessful = try {
      attemptGet(host, payload, port)
    } catch {
      case NonFatal(f) => false
    }

    if (!getSuccessful) {
      Thread.sleep(backoffPeriod)
      retryGetUntilSuccessful(host, payload, port, backoffPeriod)
    }
  }

  /**
   * Attempt a POST request once
   *
   * @param host collector host
   * @param payload event map
   * @param port collector port
   * @return Whether the request succeeded
   */
  def attemptPost(host: String, payload: Seq[Map[String, String]], port: Int = 80): Boolean = {
    val stm = System.currentTimeMillis().toString
    val payloadWithStm = payload.map(_ ++ Map("stm" -> stm))
    val req = constructPostRequest(host, payloadWithStm, port)
    val future = pipeline(req)
    val result = Await.ready(future, longTimeout).value.get
    result match {
      case Success(s) => s.status.isSuccess   // 404 match Success(_) too
      case Failure(_) => false
    }
  }

  /**
   * Attempt a POST request until successful
   *
   * @param host collector host
   * @param payload event map
   * @param port collector port
   * @param backoffPeriod How long to wait between failed requests
   */
  def retryPostUntilSuccessful( host: String, payload: Seq[Map[String, String]], port: Int = 80, backoffPeriod: Long) {
    val getSuccessful = try {
      attemptPost(host, payload, port)
    } catch {
      case NonFatal(f) => false
    }

    if (!getSuccessful) {
      Thread.sleep(backoffPeriod)
      retryPostUntilSuccessful(host, payload, port, backoffPeriod)
    }
  }

  /**
   * Close the actor system and all connections
   */
  def shutdown() {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown
  }
}
