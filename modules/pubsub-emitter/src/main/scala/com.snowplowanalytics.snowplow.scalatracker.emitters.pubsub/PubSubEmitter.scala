package com.snowplowanalytics.snowplow.scalatracker
package emitters.pubsub

import cats.effect.IO

class PubSubEmitter extends Emitter[IO] {
  override def send(event: Emitter.Payload): IO[Unit] =
    ???
}

object PubSubEmitter {

}

