/*
 * Copyright (c) 2015-2020 Snowplow Analytics Ltd. All rights reserved.
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

import io.circe.{Json, JsonObject}
import io.circe.syntax._
import io.circe.parser.parse

import scalaj.http.Http

/**
 * Module with parsing EC2-metadata logic
 *
 * @see http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html
 */
class Ec2Metadata[F[_]: Sync](client: HttpClient = _.asString) {

  val InstanceIdentitySchema =
    SchemaKey("com.amazon.aws.ec2", "instance_identity_document", "jsonschema", SchemaVer.Full(1, 0, 0))
  val InstanceIdentityUri = "http://169.254.169.254/latest/dynamic/instance-identity/document/"

  /**
   * Tries to make blocking request to EC2 instance identity document
   * On EC2 request takes ~6ms, while on non-EC2 box it blocks thread for 3 second
   *
   * @return some context or None in case of any error including 3 sec timeout
   */
  def getInstanceContextBlocking(implicit F: Concurrent[F], T: Timer[F]): F[Option[SelfDescribingJson]] =
    Concurrent.timeoutTo(getInstanceContext.map(_.some), 3.seconds, Option.empty[SelfDescribingJson].pure[F])

  /**
   * Tries to GET self-describing JSON with instance identity
   * or timeout after 10 seconds
   *
   * @return future JSON with identity data
   */
  def getInstanceContext: F[SelfDescribingJson] =
    getInstanceIdentity.map(SelfDescribingData(InstanceIdentitySchema, _))

  /**
   * Tries to GET instance identity document for EC2 instance
   *
   * @return future JSON object with identity data
   */
  def getInstanceIdentity: F[Json] = {
    val instanceIdentityDocument = getContent(InstanceIdentityUri)
    instanceIdentityDocument.flatMap { resp: String =>
      parse(resp).toOption
        .flatMap(_.asObject)
        .map(jsonObject => prepareOrThrow(jsonObject))
        .getOrElse(Sync[F].raiseError(new RuntimeException(s"Document can not be parsed: $resp")))
    }
  }

  private def prepareOrThrow(jsonObject: JsonObject): F[Json] = {
    val prepared = prepareEc2Context(jsonObject)
    if (prepared.isEmpty) {
      Sync[F].raiseError(new RuntimeException("Document contains no known keys"))
    } else {
      prepared.asJson.pure[F]
    }
  }

  /**
   * Recursively parse AWS EC2 instance metadata to get whole metadata
   *
   * @param url full url to the endpoint (usually http://169.254.169.254/latest/meta-data/)
   * @return future JSON object with metadata
   */
  def getMetadata(url: String): F[JsonObject] = {
    val key = url.split("/").last
    if (!url.endsWith("/")) { // Leaf
      getContent(url).map(value => JsonObject(key := value))
    } else { // Node
      val sublinks = getContents(url)
      val subnodes = sublinks.flatMap { links =>
        links.traverse(link => getMetadata(url + link))
      }
      val mergedObject =
        subnodes.map(_.fold(JsonObject.empty)((obj1, obj2) => JsonObject.fromMap(obj1.toMap ++ obj2.toMap)))
      mergedObject.map(obj => JsonObject(key := obj))
    }
  }

  // URL regex to for `transformUrl`
  private val publicKey = ".*/latest/meta-data/public-keys/(\\d+)\\=[A-Za-z0-9-_]+$".r

  /**
   * Handle URL which should be handled in different ways
   * e.g. we can't GET public-keys/0-key-name, we should change it to public-keys/0
   * to get data
   *
   * @param url current URL
   * @return modified URL if we're trying to get on of special cases
   */
  def transformUrl(url: String): String = url match {
    case publicKey(i) => (url.split("/").dropRight(1) :+ i).mkString("/") + "/"
    case _            => url
  }

  /**
   * Get string body of URL
   *
   * @param url leaf URL (without slash at the end)
   * @return value wrapped delayed inside F
   */
  private[metadata] def getContent(url: String): F[String] =
    Sync[F].delay(client(Http(url)).body)

  /**
   * Get content of node-link
   *
   * @param url node url (with slash at the end)
   * @return list of sublinks delayed inside F
   */
  private def getContents(url: String): F[List[String]] =
    getContent(url).map(_.split('\n').toList)

  // all keys of current instance identity schema
  private val instanceIdentityKeys = Set(
    "architecture",
    "accountId",
    "availabilityZone",
    "billingProducts",
    "devpayProductCodes",
    "imageId",
    "instanceId",
    "instanceType",
    "kernelId",
    "pendingTime",
    "privateIp",
    "ramdiskId",
    "region",
    "version"
  )

  /**
   * Make sure EC2 context contains only keys known
   * at iglu:com.amazon.aws.ec2/instance_identity_document
   *
   * @param context JSON object with EC2 context
   * @return true if object is context
   */
  private def prepareEc2Context(context: JsonObject): JsonObject =
    context.filterKeys(key => instanceIdentityKeys.contains(key))
}
