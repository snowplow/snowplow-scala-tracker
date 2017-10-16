/*
 * Copyright (c) 2015-2017 Snowplow Analytics Ltd. All rights reserved.
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
import sbt._

object Dependencies {

  object V {
    // Scala
    val scalajHttp = "2.3.0"
    val json4s     = "3.2.11"

    // Java (test only)
    val mockito    = "1.9.5"

    // Scala (test only)
    val specs2     = "3.9.5"
  }

  object Libraries {
    // Scala
    val scalajHttp    = "org.scalaj"        %% "scalaj-http"    % V.scalajHttp
    val json4sJackson = "org.json4s"        %% "json4s-jackson" % V.json4s

    // Java (test only)
    val mockito       = "org.mockito"       %  "mockito-all"    % V.mockito       % "test"

    // Scala (test only)
    val specs2        = "org.specs2"        %% "specs2-core"    % V.specs2        % "test"
  }
}
