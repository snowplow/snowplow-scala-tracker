package com.snowplowanalytics.snowplow.scalatracker.emitters

import cats.effect.{Clock, Sync}
import com.snowplowanalytics.snowplow.scalatracker.{TimeProvider, UUIDProvider}

import java.util.UUID

package object http4s {

  implicit def clockTimeProvider[F[_]: Clock]: TimeProvider[F] = new TimeProvider[F] {
    import scala.concurrent.duration.MILLISECONDS

    override def getCurrentTimeMillis: F[Long] = Clock[F].realTime(MILLISECONDS)
  }

  implicit def syncUUIDProvider[F[_]: Sync]: UUIDProvider[F] = new UUIDProvider[F] {
    override def generateUUID: F[UUID] = Sync[F].delay(UUID.randomUUID)
  }

}
