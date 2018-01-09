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

import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.collection.mutable.{Map => MMap}

import emitters.TEmitter.EmitterPayload

/**
 * Contains the map of key-value pairs making up an event
 * Must be used within single function as **not thread-safe**
 */
private[scalatracker] class Payload {

  val Encoding = "UTF-8"

  val nvPairs = MMap[String, String]()

  /**
   * Add a key-value pair
   *
   * @param name parameter name
   * @param value parameter value
   */
  def add(name: String, value: String): Unit = {
    if (!name.isEmpty && name != null && !value.isEmpty && value != null) {
      nvPairs += (name -> value)
    }
  }

  /**
   * Overloaded add function for Option. Don't modify payload for None
   *
   * @param name parameter name
   * @param value optional parameter value
   */
  def add(name: String, value: Option[String]): Unit = {
    value match {
      case Some(v) => add(name, v)
      case None    =>
    }
  }

  /** Add a map of key-value pairs one by one */
  def addDict(dict: Map[String, String]): Unit = {
    dict foreach {
      case (k, v) => add(k, v)
    }
  }

  /**
   * Stringify a JSON and add it
   *
   * @param json JSON object to encode
   * @param encodeBase64 Whether to base 64 encode the JSON
   * @param typeWhenEncoded Key to use if encodeBase64 is true
   * @param typeWhenNotEncoded Key to use if encodeBase64 is false
   */
  def addJson(
    json: JValue,
    encodeBase64: Boolean,
    typeWhenEncoded: String,
    typeWhenNotEncoded: String): Unit = {

    val jsonString = compact(render(json))

    if (encodeBase64) {
      add(typeWhenEncoded, new String(Base64.getEncoder.encode(jsonString.getBytes(Encoding)), Encoding))
    } else {
      add(typeWhenNotEncoded, jsonString)
    }
  }

  /**
   * Return the key-value pairs making up the event as an immutable map
   *
   * @return Event map
   */
  def get: EmitterPayload = Map(nvPairs.toList: _*)
}
