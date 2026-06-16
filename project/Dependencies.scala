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
    val scalajHttp       = "2.4.2"
    // org.scalaj publishes no Scala 3 build; com.codacy maintains a drop-in fork
    val scalajHttpScala3 = "2.5.0"
    val igluCore         = "1.1.4"
    val circe            = "0.14.1"
    val catsEffect       = "3.3.5"
    val http4s           = "0.23.15"

    // Java
    val slf4j       = "1.7.32"

    // Scala (test only)
    val specs2            = "4.20.9"
    val scalaCheck        = "1.17.0"
    val circeOptics       = "0.14.1"
    // circe-optics only publishes a Scala 3 build from 0.15.0 onwards
    val circeOpticsScala3 = "0.15.0"
  }

  object Libraries {
    // Scala
    val scalajHttp       = "org.scalaj"            %% "scalaj-http"     % V.scalajHttp
    val scalajHttpScala3 = "com.codacy"            %% "scalaj-http"     % V.scalajHttpScala3
    val igluCore         = "com.snowplowanalytics" %% "iglu-core"       % V.igluCore
    val igluCoreCirce    = "com.snowplowanalytics" %% "iglu-core-circe" % V.igluCore
    val circe            = "io.circe"              %% "circe-parser"    % V.circe
    val catsEffect       = "org.typelevel"         %% "cats-effect"     % V.catsEffect
    val http4sClient     = "org.http4s"            %% "http4s-client"   % V.http4s

    // Java
    val slf4jApi = "org.slf4j" % "slf4j-api" % V.slf4j

    // Scala (test only)
    val specs2            = "org.specs2"     %% "specs2-core"  % V.specs2            % "test"
    val scalaCheck        = "org.scalacheck" %% "scalacheck"   % V.scalaCheck        % "test"
    val circeOptics       = "io.circe"       %% "circe-optics" % V.circeOptics       % "test"
    val circeOpticsScala3 = "io.circe"       %% "circe-optics" % V.circeOpticsScala3 % "test"
  }

  // scalaj-http: the org.scalaj artifact for Scala 2, the com.codacy drop-in fork for Scala 3
  def scalajHttpFor(scalaVersion: String) =
    if (BuildSettings.isScala3(scalaVersion)) Libraries.scalajHttpScala3 else Libraries.scalajHttp

  // circe-optics: 0.14.x for Scala 2 because there is no 0.15.x for Scala 2.12
  def circeOpticsFor(scalaVersion: String) =
    if (BuildSettings.isScala3(scalaVersion)) Libraries.circeOpticsScala3 else Libraries.circeOptics
}
