/*
 * Copyright (c) 2015 Snowplow Analytics Ltd. All rights reserved.
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

import scala.collection.mutable.{Map => MMap}

/**
 * Class for an event subject, where we view events as of the form
 * Subject -> Verb -> Object
 */
class Subject {
  private val standardNvPairs = MMap[String, String]("p" -> Server.abbreviation)

  def setPlatform(platform: Platform): Subject = {
    standardNvPairs += ("p" -> platform.abbreviation)
    this
  }

  def setUserId(userId: String): Subject = {
    standardNvPairs += ("uid" -> userId)
    this
  }

  def setScreenResolution(width: Long, height: Long): Subject = {
    standardNvPairs += ("res" -> s"${width}x${height}")
    this
  }

  def setViewport(width: Long, height: Long): Subject = {
    standardNvPairs += ("vp" -> s"${width}x${height}")
    this
  }

  def setColorDepth(depth: Long): Subject = {
    standardNvPairs += ("cd" -> depth.toString)
    this
  }

  def setTimezone(timezone: String): Subject = {
    standardNvPairs += ("tz" -> timezone)
    this
  }

  def setLang(lang: String): Subject = {
    standardNvPairs += ("lang" -> lang)
    this
  }

  def setDomainUserId(domainUserId: String): Subject = {
    standardNvPairs += ("duid" -> domainUserId)
    this
  }

  def setIpAddress(ip: String): Subject = {
    standardNvPairs += ("ip" -> ip)
    this
  }

  def setPageUrl(url: String): Subject = {
    standardNvPairs += ("url" -> url)
    this
  }

  def setUseragent(useragent: String): Subject = {
    standardNvPairs += ("ua" -> useragent)
    this
  }

  def setNetworkUserId(nuid: String): Subject = {
    standardNvPairs += ("tnuid" -> nuid)
    this
  }

  /**
   * Retrieve the configured information as an immutable map
   *
   * @return Data map
   */
  def getSubjectInformation(): Map[String, String] = Map(standardNvPairs.toList: _*)
}
