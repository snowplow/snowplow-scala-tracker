package com.snowplowanalytics.snowplow.scalatracker.emitters.id

import cats.Id
import com.snowplowanalytics.snowplow.scalatracker.Emitter

abstract class BaseEmitter extends Emitter[Id]
