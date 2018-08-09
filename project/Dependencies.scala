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
import sbt._

object Dependencies {

  object V {
    // Scala
    val scalajHttp = "2.3.0"
    val igluCore   = "0.2.0"
    val circe      = "0.9.3"
    val catsEffect = "1.0.0-RC2"

    // Scala (test only)
    val specs2     = "4.3.2"
    val scalaCheck = "1.13.4"
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
    val circeOptics   = "io.circe"               %% "circe-optics"    % V.circe       % "test"
  }
}
