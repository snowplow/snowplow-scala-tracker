package com.snowplowanalytics.snowplow.scalatracker.emitters

import java.io

trait TEmitter {

  def httpGet(endpoint: String, evt: Map[String, String]) {
    
  }

  def blockingGet(event: Map[String, String], endpoint: String) {

  }

  def input(event: Map[String, String]): Unit
}
