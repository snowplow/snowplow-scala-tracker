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

import io.circe.{Encoder, Json}

/**
 * Contains the map of key-value pairs making up an event
 */
final case class Payload(toMap: Map[String, String] = Map.empty) extends AnyVal

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

}
