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
package com.snowplowanalytics.snowplow.scalatracker.metadata
import com.snowplowanalytics.iglu.core.{SchemaKey, SchemaVer, SelfDescribingData}
import com.snowplowanalytics.snowplow.scalatracker.SelfDescribingJson

import scala.concurrent.duration._

import cats.implicits._
import cats.effect.{Concurrent, Sync, Timer}

import io.circe.Json
import io.circe.syntax._
import io.circe.parser.parse

import scalaj.http.{Http, HttpRequest}

/**
 * Module with parsing GCE-metadata logic
 *
 * @see https://cloud.google.com/compute/docs/storing-retrieving-metadata
 *
 * Unlike EC2 instance document, GCE does not provide an excerpt, but instead
 * this module collect only meaningful properties
 */
class GceMetadata[F[_]: Sync] {

  val InstanceMetadataSchema =
    SchemaKey("com.google.cloud.gce", "instance_metadata", "jsonschema", SchemaVer.Full(1, 0, 0))
  val InstanceMetadataUri = "http://metadata.google.internal/computeMetadata/v1/instance/"

  /**
   * Tries to make blocking request to GCE instance identity document
   *
   * @return some context or None in case of any error including 3 sec timeout
   */
  def getInstanceContextBlocking(implicit F: Concurrent[F], timer: Timer[F]): F[Option[SelfDescribingJson]] =
    Concurrent.timeoutTo(getInstanceContext.map(_.some), 3.seconds, Option.empty[SelfDescribingJson].pure[F])

  /**
   * Tries to GET self-describing JSON with instance identity
   * or timeout after 10 seconds
   *
   * @return future JSON with identity data
   */
  def getInstanceContext: F[SelfDescribingJson] =
    getMetadata.map(SelfDescribingData(InstanceMetadataSchema, _))

  /** Construct metadata context */
  def getMetadata: F[Json] =
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

  private def request(path: String): HttpRequest =
    Http(InstanceMetadataUri + path).header("Metadata-Flavor", "Google")

  private[metadata] def getString(path: String): F[String] =
    Sync[F].delay(request(path).asString.body)

  private def getJson(path: String): F[Json] =
    getString(path)
      .flatMap { string =>
        Sync[F].fromEither(parse(string).map { json =>
          json.arrayOrObject(json,
                             array => if (array.isEmpty) Json.Null else json,
                             obj   => if (obj.isEmpty) Json.Null else json)
        })
      }

  private def getDir(path: String): F[Json] =
    getString(path + "?recursive=true")
      .flatMap { string =>
        Sync[F]
          .fromEither(parse(string))
          .map(json => json.withObject(obj => if (obj.isEmpty) Json.Null else json))
      }
}
