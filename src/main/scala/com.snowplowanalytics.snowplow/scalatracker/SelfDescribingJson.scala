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

object SelfDescribingJson {

  /**
   * Create a SelfDescribingJson from the individual components of a schema
   *
   * @param protocol schema path protocol
   * @param vendor schema vendor
   * @param name schema name
   * @param format schema format (eg. jsonschema)
   * @param model model number of SchemaVer
   * @param revision revision number of SchemaVer
   * @param addition addition number of SchemaVer
   * @param data JSON instance
   */
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

  /**
   * Convenience method to create an outer unstruct_event self-describing JSON
   *
   * @param schema the schema string
   * @param data Unstructured event self-describing JSOn
   */
  def apply(schema: String, data: SelfDescribingJson): SelfDescribingJson = {
    SelfDescribingJson(schema, data.toJObject)
  }

  /**
   * Convenience method to turn a sequence of contexts into a self-describing JSON
   *
   * @param schema the schema string
   * @param data Sequence of self-describing JSONs representing custom contexts
   */
  def apply(schema: String, data: Seq[SelfDescribingJson]): SelfDescribingJson = {
    SelfDescribingJson(schema, JArray(data.toList.map(_.toJObject)))
  }
}

/**
 * JSON representing an unstructured event or a custom context
 *
 * @param schema the schema string
 * @param data JSON instance
 */
case class SelfDescribingJson(schema: String, data: JValue) {
  def toJObject: JObject = ("schema" -> schema) ~ ("data" -> data)
}
