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
package com.snowplowanalytics.snowplow.scalatracker

/**
 * Class for an event subject, where we view events as of the form
 * Subject -> Verb -> Object
 */
final case class Subject(subjectInformation: Map[String, String] = Map("p" -> Server.abbreviation)) {

  def setPlatform(platform: Platform): Subject =
    Subject(subjectInformation + ("p" -> platform.abbreviation))

  def setUserId(userId: String): Subject =
    Subject(subjectInformation + ("uid" -> userId))

  def setScreenResolution(width: Long, height: Long): Subject =
    Subject(subjectInformation + ("res" -> s"${width}x${height}"))

  def setViewport(width: Long, height: Long): Subject =
    Subject(subjectInformation + ("vp" -> s"${width}x${height}"))

  def setColorDepth(depth: Long): Subject =
    Subject(subjectInformation + ("cd" -> depth.toString))

  def setTimezone(timezone: String): Subject =
    Subject(subjectInformation + ("tz" -> timezone))

  def setLang(lang: String): Subject =
    Subject(subjectInformation + ("lang" -> lang))

  def setDomainUserId(domainUserId: String): Subject =
    Subject(subjectInformation + ("duid" -> domainUserId))

  def setIpAddress(ip: String): Subject =
    Subject(subjectInformation + ("ip" -> ip))

  def setUseragent(useragent: String): Subject =
    Subject(subjectInformation + ("ua" -> useragent))

  def setNetworkUserId(nuid: String): Subject =
    Subject(subjectInformation + ("tnuid" -> nuid))

  /**
   * Retrieve the configured information as an immutable map
   *
   * @return Data map
   */
  @deprecated("The underlying map is now immutable - use subjectInformation field instead", "0.6.0")
  def getSubjectInformation(): Map[String, String] = subjectInformation
}
