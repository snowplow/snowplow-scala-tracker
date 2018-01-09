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

import java.io.FileWriter

import org.json4s.{JArray, JObject, JString, JValue}
import org.json4s.jackson.JsonMethods.{compact, parseOpt}
import org.json4s.JsonAST.JDouble

import org.scalacheck.Gen

import Tracker.{DeviceCreatedTimestamp, Timestamp, TrueTimestamp}

import com.snowplowanalytics.iglu.core.{ SelfDescribingData, SchemaKey, SchemaVer }
import com.snowplowanalytics.iglu.core.json4s.implicits._

import com.snowplowanalytics.snowplow.scalatracker.emitters.AsyncBatchEmitter
import com.snowplowanalytics.snowplow.scalatracker.emitters.TEmitter._

/**
  * Ad-hoc load testing
  */
object StressTest {

  /** ADT for all possible event types Tracker can accept */
  sealed trait EventArguments
  case class PageView(url: String, title: Option[String], referrer: Option[String], contexts: Option[List[SelfDescribingJson]], timestamp: Option[Timestamp]) extends EventArguments

  // Parser typeclass. Useless so far
  trait Read[A] { def reads(line: String): A }

  object Read {
    def apply[A: Read] = implicitly[Read[A]]
  }

  implicit val sdJsonsRead = new Read[List[SelfDescribingJson]] {
    def parseJson(json: JValue): SelfDescribingData[JValue] = {
      json match {
        case JObject(fields) =>
          val map = fields.toMap
          map.get("schema") match {
            case Some(JString(schema)) => SelfDescribingJson(schema, map("data"))
          }
      }
    }

    def reads(line: String): List[SelfDescribingJson] =
      parseOpt(line) match {
        case Some(JArray(list)) => list.map(parseJson)
        case None => Nil
      }
  }

  implicit val tstmpRead = new Read[Option[Timestamp]] {
    def reads(line: String): Option[Timestamp] = {
      line.split(":").toList match {
        case List("ttm", tstamp) => Some(TrueTimestamp(tstamp.toLong))
        case List("dtm", tstamp) => Some(DeviceCreatedTimestamp(tstamp.toLong))
        case _ => None
      }
    }
  }

  implicit val eventRead = new Read[EventArguments] {
    def reads(line: String): EventArguments = {
      val cols = line.split("\t", -1).lift
      (cols(0), cols(1)) match {
        case (Some("pv"), Some(url)) =>
          val ctx: Option[List[SelfDescribingJson]] = cols(4).map(sdJsonsRead.reads)
          val timestamp = cols(5).flatMap(tstmpRead.reads)
          PageView(url, cols(2), cols(3), ctx, timestamp)
      }
    }
  }

  // Generate valid pseudo-URL
  val urlGen = for {
    protocol <- Gen.oneOf(List("http://", "https://"))
    port <- Gen.oneOf(List("", ":80", ":8080", ":443", ":10505"))

    lengthDomain <- Gen.choose(1, 3)
    topDomain <- Gen.oneOf(List("com", "ru", "co.uk", "org", "mobi", "by"))
    domainList <- Gen.containerOfN[List, String](lengthDomain, Gen.alphaLowerStr)

    lengthUrl <- Gen.choose(0, 5)
    urlList <- Gen.containerOfN[List, String](lengthUrl, Gen.alphaNumStr)
    url = new java.net.URL(protocol + domainList.mkString(".") + s".$topDomain" + port + "/" + urlList.mkString("/"))
  } yield url

  // Generate geolocation context
  val geoLocationGen = for {
    latitude <- Gen.choose[Double](-90, 90)
    longitude <- Gen.choose[Double](-180, 180)
    data = JObject("latitude" -> JDouble(latitude), "longitude" -> JDouble(longitude))
    sd = SelfDescribingData[JValue](SchemaKey("com.snowplowanalytics.snowplow", "geolocation_context", "jsonschema", SchemaVer.Full(1,1,0)), data)
  } yield sd

  // Generate timestamp
  val timestampGen = for {
    tstType <- Gen.option(Gen.oneOf(List(TrueTimestamp.apply _, DeviceCreatedTimestamp.apply _)))
    tstamp <- Gen.choose[Long](1508316432000L - (2 * 365 * 86400 * 1000L), 1508316432000L)
    result <- tstType.map { x => x(tstamp) }
  } yield result

  // Generate whole pageview event
  val pageViewGen = for {
    url <- urlGen.map(_.toString)
    title <- Gen.option(Gen.alphaNumStr)
    referrer <- Gen.option(urlGen.map(_.toString))
    ctx <- Gen.option(geoLocationGen.map(x => List(x)))
    tstamp <- timestampGen
  } yield PageView(url, title, referrer, ctx, tstamp)

  def writeContext(sd: List[SelfDescribingData[JValue]]): String =
    compact(JArray(sd.map(s => s.normalize)))

  def writeTimestamp(tstamp: Timestamp): String = tstamp match {
    case TrueTimestamp(tst) => s"ttm:$tst"
    case DeviceCreatedTimestamp(tst) => s"dtm:$tst"
  }

  def writeEvent(event: PageView) =
    s"pv\t${event.url}\t${event.title.getOrElse("")}\t${event.referrer.getOrElse("")}\t${event.contexts.map(writeContext).getOrElse("")}\t${event.timestamp.map(writeTimestamp).getOrElse("")}"

  def write(path: String, cardinality: Int): Unit = {
    var i = 0
    val fw = new FileWriter(path)
    while (i < cardinality) {
      pageViewGen.sample.map(writeEvent) match {
        case Some(line) => fw.write(line + "\n")
        case None => ()
      }
      i = i + 1
    }
    fw.close()
  }

  /**
    * Thread imitating application's work thread that has access to tracker
    * Constructor blocks until events are not loaded into memory
    */
  class TrackerThread(path: String, tracker: Tracker) {
    // It can take some time
    val events = scala.io.Source.fromFile(path).getLines().map(Read[EventArguments].reads).toList

    println(s"TrackerThread for $path initialized")

    def getWorker: Thread = {
      val worker = new Thread {
        private var i = 0
        override def run() {
          events.foreach {
            case PageView(url, title, referrer, contexts, timestamp) =>
              tracker.trackPageView(url, title, referrer, contexts.getOrElse(Nil), timestamp)
              i = i + 1
              if (i % 1000 == 0) {
                println(s"One more 1000 from $path")
              }
          }
          println(s"TrackerThread for $path sent $i events")
          i = 0
        }
      }
      worker.setDaemon(true)
      worker
    }
  }

  /**
    * Main method. Starts specified amount of separate threads sharing a tracker,
    * each reading its own file and sending events via the same tracker.
    * All threads should be prepared (parse events and store them in memory) during
    * construction. When function returns - its ready to be started by foreach(_.run())
    * ```
    * println(System.currentTimeMillis)
    * res0.foreach(_.run())
    * res0.foreach(_.join())
    * println(System.currentTimeMillis)
    * ```
    *
    * @param collector single collector for all threads
    * @param dir directory with temporary event TSVs
    * @param cardinality amount of events in each TSV
    * @param threads amount of parallel threads
    * @return list of threads
    */
  def testAsyncBatch(collector: String, port: Int, dir: String, cardinality: Int, threads: Int = 1, callback: Option[Callback]) = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val files = List.fill(threads)(dir).zipWithIndex.map { case (path, i) => s"$path/events-$i.tsv" }
    files.foreach { file => write(file, cardinality) }
    println(s"Writing to files completed. ${files.mkString(", ")}")

    val emitter = AsyncBatchEmitter.createAndStart(collector, Some(port), bufferSize = 10)
    val tracker = new Tracker(List(emitter), "test-tracker-ns", "test-app")

    files.map(file => new TrackerThread(file, tracker).getWorker)
  }
}

