package com.snowplowanalytics.snowplow.scalatracker

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

object SelfDescribingJson {
	def apply(schema: String, data: SelfDescribingJson): SelfDescribingJson = {
		SelfDescribingJson(schema, data.toJObject)
	}

	def apply(schema: String, data: Seq[SelfDescribingJson]): SelfDescribingJson = {
		SelfDescribingJson(schema, JArray(data.toList.map(_.toJObject)))
	}
}

case class SelfDescribingJson(schema: String, data: JValue) {
	def toJObject(): JObject = ("schema" -> schema) ~ ("data" -> data)
}
