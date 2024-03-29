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

lazy val commonSettings = Seq(
  organization := "com.snowplowanalytics",
  scalaVersion := "2.13.6",
  crossScalaVersions := Seq("2.12.14", "2.13.6"),
  scalacOptions := BuildSettings.compilerOptions(scalaVersion.value),
  javacOptions ++= BuildSettings.javaCompilerOptions,
  libraryDependencies ++= Seq(
    Dependencies.Libraries.specs2,
    Dependencies.Libraries.scalaCheck,
    Dependencies.Libraries.circeOptics
  ),
  ThisBuild / dynverVTagPrefix := false
) ++ BuildSettings.publishSettings // Otherwise git tags required to have v-prefix

lazy val root = project
  .in(file("."))
  .aggregate(core, idEmitter, metadata, http4sEmitter)
  .settings(commonSettings)
  .settings(Seq(
    publish / skip := true
  ))

lazy val core = project
  .in(file("modules/core"))
  .enablePlugins(MimaPlugin)
  .settings(commonSettings)
  .settings(BuildSettings.mimaSettings)
  .settings(Seq(
    description := "Scala tracker for Snowplow",
    name := "snowplow-scala-tracker-core",
    libraryDependencies ++= Seq(
      Dependencies.Libraries.igluCore,
      Dependencies.Libraries.circe,
      Dependencies.Libraries.igluCoreCirce
    )
  ) ++ BuildSettings.scalifySettings)

lazy val idEmitter = project
  .in(file("modules/id-emitter"))
  .enablePlugins(MimaPlugin)
  .settings(commonSettings)
  .settings(BuildSettings.mimaSettings)
  .settings(Seq(
    name := "snowplow-scala-tracker-emitter-id",
    libraryDependencies ++= Seq(
      Dependencies.Libraries.scalajHttp,
      Dependencies.Libraries.slf4jApi
    )
  ))
  .dependsOn(core % "test->test;compile->compile")

lazy val metadata = project
  .in(file("modules/metadata"))
  .enablePlugins(MimaPlugin)
  .settings(commonSettings)
  .settings(BuildSettings.mimaSettings)
  .settings(Seq(
    name := "snowplow-scala-tracker-metadata",
    libraryDependencies ++= Seq(
      Dependencies.Libraries.scalajHttp,
      Dependencies.Libraries.catsEffect
    )
  ))
  .dependsOn(core % "test->test;compile->compile")

lazy val http4sEmitter = project
  .in(file("modules/http4s-emitter"))
  .enablePlugins(MimaPlugin)
  .settings(commonSettings)
  .settings(BuildSettings.mimaSettings)
  .settings(Seq(
    name := "snowplow-scala-tracker-emitter-http4s",
    libraryDependencies ++= List(
      Dependencies.Libraries.http4sClient,
      Dependencies.Libraries.slf4jApi,
      Dependencies.Libraries.catsEffect
    )
  ))
  .dependsOn(core % "test->test;compile->compile")
