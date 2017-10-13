/*
 * Copyright (c) 2015-2017 Snowplow Analytics Ltd. All rights reserved.
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
import scala.util.Success
import scala.util.control.NonFatal
import scala.concurrent.{
  Future,
  Await
}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import scalaj.http._

// json4s
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._


/**
 * Object to hold methods for sending HTTP requests
 */
object RequestUtils {
  // JSON object with Iglu URI to Schema for payload
  private val payloadBatchStub: JObject = ("schema", "iglu:com.snowplowanalytics.snowplow/payload_data/jsonschema/1-0-4")

  /**
   * Transform List of Map[String, String] to JSON array of objects
   *
   * @param payload list of string-to-string maps taken from HTTP query
   * @return JSON array represented as String
   */
  private def postPayload(payload: Seq[Map[String, String]]): String =
    compact(payloadBatchStub ~ ("data", payload))

  val longTimeout = 5.minutes

  def pipeline(request: HttpRequest): Future[HttpResponse[Array[Byte]]] =
    Future(request.asBytes)

  /**
   * Construct GET request with single event payload
   *
   * @param host URL host (not header)
   * @param payload map of event keys
   * @param port URL port (not header)
   * @param https should this request use the https scheme
   * @return HTTP request with event
   */
  private[emitters] def constructGetRequest(host: String, payload: Map[String, String], port: Int, https: Boolean = false): HttpRequest = {
    val scheme = if (https) "https://" else "http://"
    Http(s"$scheme$host:$port/i").params(payload)
  }

  /**
   * Construct POST request with batch event payload
   *
   * @param host URL host (not header)
   * @param payload list of events
   * @param port URL port (not header)
   * @param https should this request use the https scheme
   * @return HTTP request with event
   */
  private[emitters] def constructPostRequest(host: String, payload: List[Map[String, String]], port: Int, https: Boolean = false): HttpRequest = {
    val scheme = if (https) "https://" else "http://"
    Http(s"$scheme$host:$port/com.snowplowanalytics.snowplow/tp2")
      .postData(postPayload(payload))
      .header("content-type", "application/json")
  }

  /**
   * Attempt a GET request once
   *
   * @param host collector host
   * @param payload event map
   * @param port collector port
   * @param https should this request use the https scheme
   * @return Whether the request succeeded
   */
  def attemptGet(host: String, payload: Map[String, String], port: Int = 80, https: Boolean = false): Boolean = {
    val payloadWithStm = payload ++ Map("stm" -> System.currentTimeMillis().toString)
    val req = constructGetRequest(host, payloadWithStm, port, https)
    val future = pipeline(req)
    val result = Await.ready(future, longTimeout).value.get
    result match {
      case Success(s) => s.code >= 200 && s.code < 300
      case _ => false
    }
  }

  /**
   * Attempt a GET request until success or 10th try
   * Double backoff period after each failed try
   *
   * @param host collector host
   * @param payload event map
   * @param port collector port
   * @param backoffPeriod How long to wait after first failed request
   * @param attempt accumulated value of tries
   * @param https should this request use the https scheme
   */
  def retryGetUntilSuccessful(
       host: String,
       payload: Map[String, String],
       port: Int = 80,
       backoffPeriod: Long,
       attempt: Int = 1,
       https: Boolean = false) {

    val getSuccessful = try {
      attemptGet(host, payload, port, https)
    } catch {
      case NonFatal(_) => false
    }

    if (!getSuccessful && attempt < 10) {
      Thread.sleep(backoffPeriod)
      retryGetUntilSuccessful(host, payload, port, backoffPeriod * 2, attempt + 1, https)
    }
  }

  /**
   * Attempt a POST request once
   *
   * @param host collector host
   * @param payload event map
   * @param port collector port
   * @param https should this request use the https scheme
   * @return Whether the request succeeded
   */
  def attemptPost(host: String, payload: List[Map[String, String]], port: Int = 80, https: Boolean = false): Boolean = {
    val stm = System.currentTimeMillis().toString
    val payloadWithStm = payload.map(_ ++ Map("stm" -> stm))
    val req = constructPostRequest(host, payloadWithStm, port, https)
    val future = pipeline(req)
    val result = Await.ready(future, longTimeout).value.get
    result match {
      case Success(s) => s.code >= 200 && s.code < 300
      case _ => false
    }
  }

  /**
   * Attempt a POST request until success or 10th try
   * Double backoff period after each failed try
   *
   * @param host collector host
   * @param payload event map
   * @param port collector port
   * @param backoffPeriod How long to wait after first failed request
   * @param attempt accumulated value of tries
   * @param https should this request use the https scheme
   */
  def retryPostUntilSuccessful(
      host: String,
      payload: List[Map[String, String]],
      port: Int = 80,
      backoffPeriod: Long,
      attempt: Int = 1,
      https: Boolean = false) {

    val getSuccessful = try {
      attemptPost(host, payload, port, https)
    } catch {
      case NonFatal(_) => false
    }

    if (!getSuccessful && attempt < 10) {
      Thread.sleep(backoffPeriod)
      retryPostUntilSuccessful(host, payload, port, backoffPeriod * 2, attempt + 1, https)
    }
  }
}
