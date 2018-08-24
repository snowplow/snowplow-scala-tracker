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

import io.circe.Json
import io.circe.syntax._

import com.snowplowanalytics.iglu.core.{SchemaKey, SchemaVer}

/** Module supporting `trackError` tracking method */
object ErrorTracking {

  // Constants from iglu:com.snowplowanalytics.snowplow/application_error/jsonschema/1-0-1
  private val MaxMessageLength       = 2048
  private val MaxStackLength         = 8192
  private val MaxThreadNameLength    = 1024
  private val MaxClassNameLength     = 1024
  private val MaxExceptionNameLength = 1024

  val ApplicationErrorSchemaKey =
    SchemaKey("com.snowplowanalytics.snowplow", "application_error", "jsonschema", SchemaVer.Full(1, 0, 1))

  /** Transform `Throwable` to `application_error`-compatible payload */
  def toData(error: Throwable): Json = {
    val stackElement = headStackTrace(error)

    val requiredElements = List(
      "message" := truncateString(error.getMessage, MaxMessageLength).getOrElse("Null or empty message found"),
      "threadId" := Thread.currentThread.getId,
      "threadId" := Thread.currentThread.getId,
      "programmingLanguage" := "SCALA",
      "isFatal" := true
    )

    val jsonFromOptional = JsonUtils.jsonObjectWithoutNulls(
      "exceptionName" := stackElement.map(_.getLineNumber),
      "lineNumber" := stackElement.map(_.getLineNumber),
      "className" := stackElement.map(_.getClassName).flatMap(truncateString(_, MaxClassNameLength)),
      "exceptionName" := truncateString(error.getClass.getName, MaxExceptionNameLength),
      "threadName" := truncateString(Thread.currentThread.getName, MaxThreadNameLength),
      "stackTrace" := truncateString(stackTraceToString(error), MaxStackLength)
    )

    jsonFromOptional.deepMerge(Json.fromFields(requiredElements))
  }

  private def truncateString(s: String, maxLength: Int): Option[String] =
    Option(s).filter(_.nonEmpty).map(_.take(maxLength))

  private def stackTraceToString(e: Throwable): String = {
    val sw = new StringWriter
    e.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  private def headStackTrace(e: Throwable): Option[StackTraceElement] =
    e.getStackTrace.headOption
}
