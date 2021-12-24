package com.snowplowanalytics.snowplow.scalatracker.emitters

import cats.Id
import com.snowplowanalytics.snowplow.scalatracker.{TimeProvider, UUIDProvider}

import java.util.UUID

package object id {

  implicit val idTimeProvider: TimeProvider[Id] = new TimeProvider[Id] {
    override def getCurrentTimeMillis: Id[Long] = System.currentTimeMillis()
  }

  implicit val idUUIDProvider: UUIDProvider[Id] = new UUIDProvider[Id] {
    override def generateUUID: Id[UUID] = UUID.randomUUID()
  }

}
