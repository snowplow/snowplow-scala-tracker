package com.snowplowanalytics.snowplow.scalatracker.emitters.id

import java.util.UUID

import cats.Id
import com.snowplowanalytics.snowplow.scalatracker.Emitter

abstract class BaseEmitter extends Emitter[Id] {

  override def generateUUID: Id[UUID] = UUID.randomUUID()

  override def getCurrentMilliseconds: Id[Long] = System.currentTimeMillis()

}
