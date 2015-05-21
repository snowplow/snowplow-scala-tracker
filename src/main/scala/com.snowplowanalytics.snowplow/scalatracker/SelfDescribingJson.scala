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

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

object SelfDescribingJson {

  def apply(
    protocol: String,
    vendor: String,
    name: String,
    format: String,
    model: Int,
    revision: Int,
    addition: Int,
    data: JValue): SelfDescribingJson = {

    val schema = s"$protocol:$vendor/$name/$format/$model-$revision-$addition"

    SelfDescribingJson(schema, data)
  }

  def apply(schema: String, data: SelfDescribingJson): SelfDescribingJson = {
    SelfDescribingJson(schema, data.toJObject)
  }

  def apply(schema: String, data: Seq[SelfDescribingJson]): SelfDescribingJson = {
    SelfDescribingJson(schema, JArray(data.toList.map(_.toJObject)))
  }
}

case class SelfDescribingJson(schema: String, data: JValue) {
  def toJObject(): JObject = ("schema" -> schema) ~ ("data" -> data)
}
