/*
 * Copyright (c) 2013-2020 Snowplow Analytics Ltd. All rights reserved.
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

import scala.Seq

// dynver plugin
import sbtdynver.DynVerPlugin.autoImport._

// Mima plugin
import com.typesafe.tools.mima.plugin.MimaKeys._
import com.typesafe.tools.mima.plugin.MimaPlugin

object BuildSettings {

  // Makes our SBT settings available in runtime
  lazy val scalifySettings = Seq(
    Compile / sourceGenerators += Def.task {
      val file = (Compile / sourceManaged).value / "settings.scala"
      IO.write(file, """package com.snowplowanalytics.snowplow.scalatracker.generated
                       |object ProjectSettings {
                       |  val version = "%s"
                       |  val name = "%s"
                       |  val organization = "%s"
                       |  val scalaVersion = "%s"
                       |}
                       |""".stripMargin.format(version.value, name.value, organization.value, scalaVersion.value))
      Seq(file)
    }.taskValue
  )

  def compilerOptions(scalaVersion: String) = Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-unchecked",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Xlint"
  ) ++ (if (priorTo2_13(scalaVersion)) Seq("-Ypartial-unification", "-Xfuture") else Nil )

  lazy val javaCompilerOptions = Seq(
    "-source", "1.8",
    "-target", "1.8"
  )

  def priorTo2_13(scalaVersion: String): Boolean =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, minor)) if minor < 13 => true
      case _                              => false
    }

  // Maven publishing settings
  lazy val publishSettings = Seq[Setting[_]](
    publishArtifact := true,
    Test / publishArtifact := false,
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),
    pomIncludeRepository := { _ => false },
    homepage := Some(url("http://snowplowanalytics.com")),
    developers := List(
      Developer(
        "Snowplow Analytics Ltd",
        "Snowplow Analytics Ltd",
        "support@snowplowanalytics.com",
        url("https://snowplowanalytics.com")
      )
    )
  )

  // If a new version introduces breaking changes,
  // clear `mimaBinaryIssueFilters` and `mimaPreviousVersions`.
  // Otherwise, add previous version to the set without
  // removing older versions.
  val mimaPreviousVersions = Set()
  val mimaSettings = Seq(
    mimaPreviousArtifacts := mimaPreviousVersions.map { organization.value %% name.value % _ },
    ThisBuild / mimaFailOnNoPrevious := false,
    mimaBinaryIssueFilters ++= Seq(),
    Test / test := {
      mimaReportBinaryIssues.value
      (Test / test).value
    }
  )
  
}
