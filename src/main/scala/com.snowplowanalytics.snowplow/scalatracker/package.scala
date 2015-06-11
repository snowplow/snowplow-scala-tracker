package com.snowplowanalytics.snowplow

package object scalatracker {

  type ??? = Any

  def notSupported(message: String) = throw new Error(message)

  // Tracker event parameters

  /*
  	Unstructure event key 
  */
  val UE_PX = "ue_px" // using base 64 encoding
  val UE_PR = "ue_pr"
}