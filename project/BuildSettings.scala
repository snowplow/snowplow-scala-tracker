/*
 * Copyright (c) 2013-2015 Snowplow Analytics Ltd. All rights reserved.
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
import Keys._

object BuildSettings {

  // format: OFF
  // Basic settings for our app
  lazy val basicSettings = Seq[Setting[_]](
    organization          :=  "com.snowplowanalytics",
    version               :=  "0.1.0",
    description           :=  "Scala tracker for Snowplow",
    scalaVersion          :=  "2.10.4",
    crossScalaVersions    :=  Seq("2.10.4", "2.11.5"), 
    scalacOptions         :=  Seq("-deprecation", "-encoding", "utf8"),
    resolvers             ++= Dependencies.resolutionRepos,
    scalacOptions         ++= Seq(
      "-feature",
      "-deprecation",
      "-language:implicitConversions",
      "-language:postfixOps",
      "-unchecked",
      "-Xlint")
  )

  // Makes our SBT app settings available from within the ETL
  lazy val scalifySettings = Seq(sourceGenerators in Compile <+= (sourceManaged in Compile, version, name, organization, scalaVersion) map { (d, v, n, o, sv) =>
    val file = d / "settings.scala"
    IO.write(file, """package com.snowplowanalytics.snowplow.scalatracker.generated
      |object ProjectSettings {
      |  val version = "%s"
      |  val name = "%s"
      |  val organization = "%s"
      |  val scalaVersion = "%s"
      |}
      |""".stripMargin.format(v, n, o, sv))
    Seq(file)
  })

  // Publish settings
  // TODO: update with ivy credentials etc when we start using Nexus
  lazy val publishSettings = Seq[Setting[_]](
    // Enables publishing to maven repo
    publishMavenStyle := true,

    publishTo <<= version { version =>
      val basePath = "target/repo/%s".format {
        if (version.trim.endsWith("SNAPSHOT")) "snapshots/" else "releases/"
      }
      Some(Resolver.file("Local Maven repository", file(basePath)) transactional())
    }
  )

  lazy val buildSettings = basicSettings ++ scalifySettings ++ publishSettings
}
