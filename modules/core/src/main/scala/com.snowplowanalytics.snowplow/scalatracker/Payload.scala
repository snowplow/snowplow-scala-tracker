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

import java.util.Base64

import io.circe.Json

/**
 * Contains the map of key-value pairs making up an event
 * Must be used within single function as **not thread-safe**
 */
private[scalatracker] final case class Payload(private val nvPairs: Map[String, String] = Map.empty) extends AnyVal {

  /**
   * Add a key-value pair
   *
   * @param name parameter name
   * @param value parameter value
   */
  def add(name: String, value: String): Payload =
    addDict(Map(name -> value))

  /**
   * Overloaded add function for Option. Don't modify payload for None
   *
   * @param name parameter name
   * @param value optional parameter value
   */
  def add(name: String, value: Option[String]): Payload =
    value match {
      case Some(v) => add(name, v)
      case None    => this
    }

  /** Add a map of key-value pairs one by one */
  def addDict(dict: Map[String, String]): Payload = {
    val filtered = dict.filter { case (key, value) => key != null && value != null && !key.isEmpty && !value.isEmpty }
    Payload(nvPairs ++ filtered)
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
      add(typeWhenEncoded, new String(Base64.getEncoder.encode(jsonString.getBytes("UTF-8")), "UTF-8"))
    } else {
      add(typeWhenNotEncoded, jsonString)
    }
  }

  /**
   * Return the key-value pairs making up the event as an immutable map
   *
   * @return Event map
   */
  def get: Emitter.Payload = nvPairs
}
