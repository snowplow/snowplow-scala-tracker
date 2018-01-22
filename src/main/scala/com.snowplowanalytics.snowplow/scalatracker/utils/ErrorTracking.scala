package com.snowplowanalytics.snowplow.scalatracker.utils

import java.io.{PrintWriter, StringWriter}

import com.snowplowanalytics.iglu.core.{SchemaKey, SchemaVer}

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
      version = SchemaVer(1, 0, 0)
  )

  def truncateString(s: String, maxLength: Int): Option[String] =
    Option(s).filter(_.nonEmpty).map(_.substring(0, Math.min(s.length, maxLength)))

  def stackTraceToString(e: Throwable): String = {
    val sw = new StringWriter
    e.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  def headStackTrace(e: Throwable): Option[StackTraceElement] =
    if (e.getStackTrace.length > 0) Some(e.getStackTrace()(0)) else None
}
