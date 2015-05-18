package com.snowplowanalytics.snowplow.scalatracker

import org.apache.commons.codec.binary.Base64

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import scala.collection.mutable.{Map => MMap}

class Payload {

  val Encoding = "UTF-8"

  val nvPairs = MMap[String, String]()

  def add(name: String, value: String) {
    if (!name.isEmpty && name != null && !value.isEmpty && value != null) {
      nvPairs += (name -> value)
    }
  }

  def addJson(
    json: JObject,
    encodeBase64: Boolean,
    typeWhenEncoded: String,
    typeWhenNotEncoded: String) {

    val jsonString = compact(render(json))

    if (encodeBase64) {
      add(typeWhenEncoded, new String(Base64.encodeBase64(jsonString.getBytes(Encoding)), Encoding))
    } else {
      add(typeWhenNotEncoded, jsonString)
    }
  }

  def get(): Map[String, String] = Map(nvPairs.toList: _*)

}
