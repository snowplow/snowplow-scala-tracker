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

import cats.implicits._
import cats.effect.kernel.Async
import scalaj.http.{HttpRequest, HttpResponse}

package object metadata {

  private[metadata] type HttpClient = HttpRequest => HttpResponse[String]

  implicit class TrackerMetadataOps[F[_]](val tracker: Tracker[F]) extends AnyVal {

    /**
      * Adds EC2 context to each sent event
      * Blocks event queue until either context resolved or timed out
      */
    def enableEc2Context[G[_]: Async](): G[Tracker[F]] =
      new Ec2Metadata[G]().getInstanceContext.map(metadata => tracker.addContext(metadata))

    /**
      * Adds GCP context to each sent event
      * Blocks event queue until either context resolved or timed out
      */
    def enableGceContext[G[_]: Async](): G[Tracker[F]] =
      new GceMetadata[G]().getInstanceContext.map(metadata => tracker.addContext(metadata))

  }

}
