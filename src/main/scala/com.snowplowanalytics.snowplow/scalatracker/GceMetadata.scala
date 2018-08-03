/*
 * Copyright (c) 2015-2018 Snowplow Analytics Ltd. All rights reserved.
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

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

import cats.syntax.either._

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax._

import scalaj.http._

import com.snowplowanalytics.iglu.core.{SchemaKey, SchemaVer, SelfDescribingData}

import scala.util.{Failure, Success}

/**
 * Module with parsing GCE-metadata logic
 * @see https://cloud.google.com/compute/docs/storing-retrieving-metadata
 *
 * Unlike EC2 instance document, GCE does not provide an excerpt, but instead
 * this module collect only meaningful properties
 */
object GceMetadata {

  val InstanceMetadataSchema =
    SchemaKey("com.google.cloud.gce", "instance_metadata", "jsonschema", SchemaVer.Full(1, 0, 0))
  val InstanceMetadataUri = "http://metadata.google.internal/computeMetadata/v1/instance/"

  private var contextSlot: Option[SelfDescribingJson] = None

  /** Retrieve some context if available or nothing in case of any error */
  def context: Option[SelfDescribingJson] = contextSlot

  /**
   * Tries to make blocking request to EC2 instance identity document
   * On EC2 request takes ~6ms, while on non-EC2 box it blocks thread for 3 second
   *
   * @return some context or None in case of any error including 3 sec timeout
   */
  def getInstanceContextBlocking: Option[SelfDescribingJson] =
    try {
      Some(Await.result(getInstanceContextFuture, 3.seconds))
    } catch {
      case NonFatal(_) => None
    }

  /** Set callback on successful instance metadata GET request */
  def initializeContextRequest(): Unit =
    getInstanceContextFuture.onComplete {
      case Success(json)  => contextSlot = Some(json)
      case Failure(error) => System.err.println(s"Unable to retrieve GCP context. ${error.getMessage}")
    }

  /**
   * Tries to GET self-describing JSON with instance identity
   * or timeout after 10 seconds
   *
   * @return future JSON with identity data
   */
  def getInstanceContextFuture: Future[SelfDescribingJson] =
    getMetadata.map(SelfDescribingData(InstanceMetadataSchema, _))

  /** Construct metadata context */
  def getMetadata: Future[Json] =
    for {
      cpuPlatform <- getString("cpu-platform")
      hostname    <- getString("hostname")
      id          <- getString("id")
      image       <- getString("image")
      machineType <- getString("machine-type")
      name        <- getString("name")
      tags        <- getJson("tags")
      zone        <- getString("zone")
      attributes  <- getDir("attributes/")
    } yield
      Json.obj(
        "cpuPlatform" := cpuPlatform,
        "hostname" := hostname,
        "id" := id,
        "image" := image,
        "machineType" := machineType,
        "name" := name,
        "tags" := tags,
        "zone" := zone,
        "attributes" := attributes
      )

  def request(path: String) =
    Http(InstanceMetadataUri + path).header("Metadata-Flavor", "Google")

  private def getString(path: String): Future[String] =
    Future(request(path).asString.body)

  private def getJson(path: String): Future[Json] =
    Future.fromTry(
      parse(request(path).asString.body)
        .map(
          json =>
            json.arrayOrObject(
              json,
              array => if (array.isEmpty) Json.Null else json,
              obj   => if (obj.isEmpty) Json.Null else json
          ))
        .toTry
    )

  private def getDir(path: String): Future[Json] =
    Future.fromTry(
      parse(request(path + "?recursive=true").asString.body)
        .map(json => json.withObject(obj => if (obj.isEmpty) Json.Null else json))
        .toTry
    )
}
