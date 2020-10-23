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
import sbt._

object Dependencies {

  object V {
    // Scala
    val scalajHttp  = "2.4.2"
    val igluCore    = "1.0.0"
    val circe       = "0.13.0"
    val circeOptics = "0.13.0"
    val catsEffect  = "2.2.0"

    // Scala (test only)
    val specs2      = "4.10.5"
    val scalaCheck  = "1.14.3"
  }

  object Libraries {
    // Scala
    val scalajHttp     = "org.scalaj"            %% "scalaj-http"     % V.scalajHttp
    val igluCore       = "com.snowplowanalytics" %% "iglu-core"       % V.igluCore
    val igluCoreCirce  = "com.snowplowanalytics" %% "iglu-core-circe" % V.igluCore
    val circe          = "io.circe"              %% "circe-parser"    % V.circe
    val catsEffect     = "org.typelevel"         %% "cats-effect"     % V.catsEffect

    // Scala (test only)
    val specs2        = "org.specs2"             %% "specs2-core"     % V.specs2      % "test"
    val specs2Mock    = "org.specs2"             %% "specs2-mock"     % V.specs2      % "test"
    val scalaCheck    = "org.scalacheck"         %% "scalacheck"      % V.scalaCheck  % "test"
    val circeOptics   = "io.circe"               %% "circe-optics"    % V.circeOptics % "test"
  }
}
