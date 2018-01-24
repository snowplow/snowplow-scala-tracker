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
package com.snowplowanalytics.snowplow.scalatracker.utils

import java.io.{PrintWriter, StringWriter}

import com.snowplowanalytics.iglu.core.{SchemaKey, SchemaVer}

/**
  * Class to support util methods for tracking errors.
  */
object ErrorTracking {

  val MaxMessageLength       = 2048
  val MaxStackLength         = 8096
  val MaxThreadNameLength    = 1024
  val MaxClassNameLength     = 1024
  val MaxExceptionNameLength = 1024

  val ApplicationErrorSchemaKey: SchemaKey = SchemaKey(
      vendor = "com.snowplowanalytics.snowplow",
      name = "application_error",
      format = "jsonschema",
      version = SchemaVer(1, 0, 1)
  )

  def truncateString(s: String, maxLength: Int): Option[String] =
    Option(s).filter(_.nonEmpty).map(_.substring(0, Math.min(s.length, maxLength)))

  def stackTraceToString(e: Throwable): String = {
    val sw = new StringWriter
    e.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  def headStackTrace(e: Throwable): Option[StackTraceElement] =
    e.getStackTrace.headOption
}
