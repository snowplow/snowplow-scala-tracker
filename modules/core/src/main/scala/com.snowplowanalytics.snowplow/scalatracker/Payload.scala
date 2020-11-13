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
package com.snowplowanalytics.snowplow.scalatracker

import java.util.Base64

import io.circe._
import io.circe.syntax._
import com.snowplowanalytics.iglu.core.SelfDescribingData
import com.snowplowanalytics.iglu.core.circe.implicits._

/**
 * Contains the map of key-value pairs making up an event
 */
case class Payload(toMap: Map[String, String] = Map.empty) extends AnyVal

object Payload {

  implicit val payloadEncoder: Encoder[Payload] =
    Encoder.encodeMap[String, String].contramap[Payload](_.toMap)

  private[scalatracker] implicit class PayloadSyntax(payload: Payload) {

    /**
     * Add a key-value pair
     *
     * @param name parameter name
     * @param value parameter value
     */
    def add(name: String, value: String): Payload =
      payload.addDict(Map(name -> value))

    /**
     * Overloaded add function for Option. Don't modify payload for None
     *
     * @param name parameter name
     * @param value optional parameter value
     */
    def add(name: String, value: Option[String]): Payload =
      value match {
        case Some(v) => payload.add(name, v)
        case None    => payload
      }

    /** Add a map of key-value pairs one by one */
    def addDict(dict: Map[String, String]): Payload = {
      val filtered = dict.filter { case (key, value) => key != null && value != null && !key.isEmpty && !value.isEmpty }
      Payload(payload.toMap ++ filtered)
    }

    /**
     * Stringify a JSON and add it
     *
     * @param json JSON object to encode
     * @param encodeBase64 Whether to base 64 encode the JSON
     * @param typeWhenEncoded Key to use if encodeBase64 is true
     * @param typeWhenNotEncoded Key to use if encodeBase64 is false
     */
    def addJson(json: Json, encodeBase64: Boolean, typeWhenEncoded: String, typeWhenNotEncoded: String): Payload = {
      val jsonString = json.noSpaces

      if (encodeBase64) {
        payload.add(typeWhenEncoded, new String(Base64.getEncoder.encode(jsonString.getBytes("UTF-8")), "UTF-8"))
      } else {
        payload.add(typeWhenNotEncoded, jsonString)
      }
    }
  }

  private[scalatracker] def sizeOf(payloads: Seq[Payload]): Int =
    postPayload(payloads).getBytes.length

  /* Calculates how many extra bytes one more extra payload contributes to a json post request
   *
   * Assumes this is not the first payload in the array, and therefore adds 1 for the comma required to separate payloads
   */
  private[scalatracker] def sizeContributionOf(payload: Payload): Int =
    payload.asJson.noSpaces.getBytes.length + 1

  /**
   * Transform List of Map[String, String] to JSON array of objects
   *
   * @param payload list of string-to-string maps taken from HTTP query
   * @return JSON array represented as String
   */
  private[scalatracker] def postPayload(payload: Seq[Payload]): String =
    SelfDescribingData[Json](Tracker.PayloadDataSchemaKey, payload.asJson).normalize.noSpaces

}
