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

import java.io.{PrintWriter, StringWriter}

import org.json4s.JsonDSL._

import scala.util.control.NonFatal

object TrackerExceptionHandler {

  /**
    * ExceptionHandler class
    *
    * @param tracker Tracker
    */
  implicit class ExceptionHandler(tracker: Tracker) {

    private val MaxMessageLength       = 2048
    private val MaxStackLength         = 8096
    private val MaxThreadNameLength    = 1024
    private val MaxClassNameLength     = 1024
    private val MaxExceptionNameLength = 1024

    /**
      * Sends a Snowplow Event when error is non fatal and then re-throws.
      *
      * @param error The throwable
      */
    def errorHandler(error: Throwable): Unit = error match {
      case NonFatal(e) =>
        val stackElement = headStackTrace(e)

        val data =
          ("message"               -> truncateString(e.getMessage, MaxMessageLength).getOrElse("Null or empty message found")) ~
            ("stackTrace"          -> truncateString(stackTraceToString(e), MaxStackLength)) ~
            ("threadName"          -> truncateString(Thread.currentThread.getName, MaxThreadNameLength)) ~
            ("threadId"            -> Thread.currentThread.getId.toString) ~
            ("programmingLanguage" -> "SCALA") ~
            ("lineNumber"          -> stackElement.map(_.getLineNumber.toString)) ~
            ("className"           -> stackElement.map(_.getClassName).flatMap(truncateString(_, MaxClassNameLength))) ~
            ("exceptionName"       -> truncateString(e.getClass.getName, MaxExceptionNameLength)) ~
            ("isFatal"             -> true.toString)

        val envelope = SelfDescribingJson(
            schema = "iglu:com.snowplowanalytics.snowplow/application_error/jsonschema/1-0-0",
            data = data
        )

        val payload = new Payload()

        payload.addJson(
            json = envelope.toJObject,
            encodeBase64 = tracker.encodeBase64,
            typeWhenEncoded = "ue_px",
            typeWhenNotEncoded = "ue_pr"
        )

        tracker.track(payload)
      case _ =>
    }
  }

  private def truncateString(s: String, maxLength: Int): Option[String] =
    Option(s).map(_.substring(0, Math.min(s.length, maxLength)))

  private def stackTraceToString(e: Throwable): String = {
    val sw = new StringWriter
    e.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  private def headStackTrace(e: Throwable): Option[StackTraceElement] =
    if (e.getStackTrace.length > 0) Some(e.getStackTrace()(0)) else None
}
