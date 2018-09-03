/*
 * Copyright (c) 2013-2018 Snowplow Analytics Ltd. All rights reserved.
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
  version := "0.6.0",
  scalaVersion := "2.12.6",
  crossScalaVersions := Seq("2.11.12", "2.12.6"),
  scalacOptions := BuildSettings.compilerOptions,
  javacOptions ++= BuildSettings.javaCompilerOptions,
  libraryDependencies ++= Seq(
    Dependencies.Libraries.specs2,
    Dependencies.Libraries.specs2Mock,
    Dependencies.Libraries.scalaCheck,
    Dependencies.Libraries.circeOptics
  )
) ++ BuildSettings.buildSettings ++ BuildSettings.formattingSettings

lazy val root = project
  .in(file("."))
  .aggregate(core, idEmitter, metadata)
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
    )
  ))

lazy val idEmitter = project
  .in(file("modules/id-emitter"))
  .settings(commonSettings)
  .settings(Seq(
    name := "snowplow-scala-tracker-emitter-id",
    libraryDependencies ++= Seq(
      Dependencies.Libraries.scalajHttp,
    )
  ))
  .dependsOn(core)

lazy val metadata = project
  .in(file("modules/metadata"))
  .settings(commonSettings)
  .settings(Seq(
    name := "snowplow-scala-tracker-metadata",
    libraryDependencies ++= Seq(
      Dependencies.Libraries.scalajHttp,
      Dependencies.Libraries.catsEffect
    )
  ))
  .dependsOn(core)
