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
    val igluCore    = "1.0.1"
    val circe       = "0.14.1"
    val catsEffect  = "2.5.3"
    val http4s      = "0.22.4"

    // Java
    val slf4j       = "1.7.32"

    // Scala (test only)
    val specs2      = "4.12.3"
    val scalaCheck  = "1.15.4"
  }

  object Libraries {
    // Scala
    val scalajHttp     = "org.scalaj"            %% "scalaj-http"     % V.scalajHttp
    val igluCore       = "com.snowplowanalytics" %% "iglu-core"       % V.igluCore
    val igluCoreCirce  = "com.snowplowanalytics" %% "iglu-core-circe" % V.igluCore
    val circe          = "io.circe"              %% "circe-parser"    % V.circe
    val catsEffect     = "org.typelevel"         %% "cats-effect"     % V.catsEffect
    val http4sClient   = "org.http4s"            %% "http4s-client"   % V.http4s

    // Java
    val slf4jApi = "org.slf4j" % "slf4j-api" % V.slf4j

    // Scala (test only)
    val specs2        = "org.specs2"             %% "specs2-core"         % V.specs2      % "test"
    val scalaCheck    = "org.scalacheck"         %% "scalacheck"          % V.scalaCheck  % "test"
    val circeOptics   = "io.circe"               %% "circe-optics"        % V.circe       % "test"
  }
}
