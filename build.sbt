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
  version := "0.7.0",
  scalaVersion := "2.13.3",
  crossScalaVersions := Seq("2.12.12", "2.13.3"),
  scalacOptions := BuildSettings.compilerOptions(scalaVersion.value),
  javacOptions ++= BuildSettings.javaCompilerOptions,
  libraryDependencies ++= Seq(
    Dependencies.Libraries.specs2,
    Dependencies.Libraries.scalaCheck,
    Dependencies.Libraries.circeOptics,
    compilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full)
  )
) ++ BuildSettings.buildSettings ++ BuildSettings.formattingSettings

lazy val root = project
  .in(file("."))
  .aggregate(core, idEmitter, metadata, http4sEmitter)
  .settings(Seq(
    skip in publish := true
  ))

lazy val core = project
  .in(file("modules/core"))
  .settings(commonSettings)
  .settings(Seq(
    description := "Scala tracker for Snowplow",
    name := "snowplow-scala-tracker-core",
    libraryDependencies ++= Seq(
      Dependencies.Libraries.igluCore,
      Dependencies.Libraries.circe,
      Dependencies.Libraries.igluCoreCirce,
      Dependencies.Libraries.catsEffect,
    )
  ) ++ BuildSettings.scalifySettings)

lazy val idEmitter = project
  .in(file("modules/id-emitter"))
  .settings(commonSettings)
  .settings(Seq(
    name := "snowplow-scala-tracker-emitter-id",
    libraryDependencies ++= Seq(
      Dependencies.Libraries.scalajHttp,
    )
  ))
  .dependsOn(core % "test->test;compile->compile")

lazy val metadata = project
  .in(file("modules/metadata"))
  .settings(commonSettings)
  .settings(Seq(
    name := "snowplow-scala-tracker-metadata",
    libraryDependencies ++= Seq(
      Dependencies.Libraries.scalajHttp,
      Dependencies.Libraries.specs2Mock
    )
  ))
  .dependsOn(core % "test->test;compile->compile")

lazy val http4sEmitter = project
  .in(file("modules/http4s-emitter"))
  .settings(commonSettings)
  .settings(Seq(
    name := "snowplow-scala-tracker-emitter-http4s",
    libraryDependencies ++= List(
      Dependencies.Libraries.http4sClient
    )
  ))
  .dependsOn(core % "test->test;compile->compile")
