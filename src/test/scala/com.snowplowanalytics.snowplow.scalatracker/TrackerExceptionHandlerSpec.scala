package com.snowplowanalytics.snowplow.scalatracker

import com.snowplowanalytics.snowplow.scalatracker.emitters.TEmitter
import org.specs2.mutable.Specification
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.specs2.specification.Scope

class TrackerExceptionHandlerSpec extends Specification {

  trait DummyTracker extends Scope {

    val emitter = new TEmitter {
      var lastInput = Map[String, String]()

      override def input(event: Map[String, String]): Unit = lastInput = event
    }

    val tracker = new Tracker(List(emitter), "mytracker", "myapp", false)
  }

  "TrackerExceptionHandler" should {

    import TrackerExceptionHandler._
    import DefaultReaders._

    "tracks an exception" in new DummyTracker {

      val error = new RuntimeException("boom!")
      tracker.errorHandler(error)

      val event = emitter.lastInput

      val envelope = parse(event("ue_pr"))
      (envelope \ "schema")
        .as[String] mustEqual "iglu:com.snowplowanalytics.snowplow/application_error/jsonschema/1-0-0"

      val payload = envelope \ "data"

      (payload \ "message").as[String] mustEqual "boom!"
      (payload \ "stackTrace").as[String] must contain("java.lang.RuntimeException: boom!")
      (payload \ "threadName").as[String] must not(beEmpty)
      (payload \ "threadId").as[String] must not(beEmpty)
      (payload \ "programmingLanguage").as[String] mustEqual "SCALA"
      (payload \ "lineNumber").as[String].toInt must be greaterThan 0
      (payload \ "className").as[String] must contain(this.getClass.getName)
      (payload \ "exceptionName").as[String] mustEqual "java.lang.RuntimeException"
      (payload \ "isFatal").as[String] mustEqual "true"
    }

    "uses default message" >> {
      "when there is no error message" in new DummyTracker {
        val error = new RuntimeException()
        tracker.errorHandler(error)

        val event   = emitter.lastInput
        val payload = parse(event("ue_pr")) \ "data"

        (payload \ "message").as[String] mustEqual "Null or empty message found"
      }

      "when error messaga is empty" in new DummyTracker {
        val error = new RuntimeException("")
        tracker.errorHandler(error)

        val event   = emitter.lastInput
        val payload = parse(event("ue_pr")) \ "data"

        (payload \ "message").as[String] mustEqual "Null or empty message found"
      }
    }
  }
}
