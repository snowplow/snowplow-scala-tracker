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

import org.apache.commons.codec.binary.Base64

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import scala.collection.mutable.{Map => MMap}

class Payload {

  val Encoding = "UTF-8"

  val nvPairs = MMap[String, String]()

  def add(name: String, value: String) {
    if (!name.isEmpty && name != null && !value.isEmpty && value != null) {
      nvPairs += (name -> value)
    }
  }

  def addJson(
    json: JObject,
    encodeBase64: Boolean,
    typeWhenEncoded: String,
    typeWhenNotEncoded: String) {

    val jsonString = compact(render(json))

    if (encodeBase64) {
      add(typeWhenEncoded, new String(Base64.encodeBase64(jsonString.getBytes(Encoding)), Encoding))
    } else {
      add(typeWhenNotEncoded, jsonString)
    }
  }

  def get(): Map[String, String] = Map(nvPairs.toList: _*)

}
