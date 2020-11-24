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

import java.net.URI
import cats.implicits._

/**
 * Emitters are entities in charge of transforming events sent from tracker
 * into actual HTTP requests (IO), which includes:
 * + Async/Multi-threading
 * + Queuing `EmitterPayload`
 * + Transforming `EmitterPayload` into Bytes
 * + Backup queue and callbacks
 *
 */
trait Emitter[F[_]] {
  import Emitter._

  /**
   * Emits the event payload
   *
   * @param event Fully assembled event
   */
  def send(event: EmitterPayload): F[Unit]

}

object Emitter {

  /** Low-level representation of event */
  type EmitterPayload = Map[String, String]

  /** Collector preferences */
  case class EndpointParams(host: String, port: Int, https: Boolean) {

    /** Return stringified collector representation, e.g. `https://splw.acme.com:80/` */
    def getUri: String = s"${if (https) "https" else "http"}://$host:$port"

    def getPostUri: String = getUri ++ "/com.snowplowanalytics.snowplow/tp2"

    def getGetUri: String = getUri ++ "/i"
  }

  object EndpointParams {

    /** Construct collector preferences with correct default port */
    def apply(host: String, port: Option[Int], https: Option[Boolean]): EndpointParams =
      (port, https) match {
        case (Some(p), s)    => EndpointParams(host, p, s.getOrElse(false))
        case (None, Some(s)) => EndpointParams(host, if (s) 443 else 80, s)
        case (None, None)    => EndpointParams(host, 80, false)
      }

    /** Construct collector preferences from URI, fail on any unexpected element */
    def fromUri(uri: URI): Either[String, EndpointParams] = {
      val https: Either[String, Option[Boolean]] = Option(uri.getScheme) match {
        case Some("http")  => Some(false).asRight
        case Some("https") => Some(true).asRight
        case Some(other)   => s"Scheme $other is not supported, http and https only".asLeft
        case None          => None.asRight
      }
      val host     = Option(uri.getHost).toRight("Host must be present")
      val port     = Option(uri.getPort).filterNot(_ === -1)
      val fragment = Option(uri.getFragment).map(s => s"Fragment is not allowed in collector URI, $s given").toLeft(())
      val userinfo = Option(uri.getUserInfo).map(s => s"Userinfo is not allowed in collector URI, $s given").toLeft(())
      val path =
        Option(uri.getPath).filterNot(_.isEmpty).map(s => s"Path is not allowed in collector URI, $s given").toLeft(())
      val query     = Option(uri.getQuery).map(s => s"Query is not allowed in collector URI, $s given").toLeft(())
      val authority = Option(uri.getQuery).map(s => s"Authority is not allowed in collector URI, $s given").toLeft(())

      for {
        h <- host
        s <- https
        _ <- fragment
        _ <- userinfo
        _ <- path
        _ <- query
        _ <- authority
      } yield EndpointParams(h, port, s)
    }
  }

}
