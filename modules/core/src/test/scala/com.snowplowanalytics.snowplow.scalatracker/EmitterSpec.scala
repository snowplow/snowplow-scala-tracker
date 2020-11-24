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

import Emitter.EndpointParams

// Specs2
import org.specs2.mutable.Specification

class EmitterSpec extends Specification {
  "fromUri" should {

    "convert valid collector URIs" in {

      EndpointParams.fromUri(new URI("http://example.com")) must beRight(EndpointParams("example.com", 80, false))
      EndpointParams.fromUri(new URI("https://example.com")) must beRight(EndpointParams("example.com", 443, true))
      EndpointParams.fromUri(new URI("http://example.com:9090")) must beRight(
        EndpointParams("example.com", 9090, false))
    }

    "reject invalid collector URIs" in {

      EndpointParams.fromUri(new URI("http://example.com#fragment")) must beLeft
      EndpointParams.fromUri(new URI("http://example.com/with/path")) must beLeft
      EndpointParams.fromUri(new URI("http://user@example.com")) must beLeft
      EndpointParams.fromUri(new URI("http://example.com?abc=xyz")) must beLeft
    }
  }
}
