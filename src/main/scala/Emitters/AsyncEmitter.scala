package com.snowplowanalytics.snowplow.scalatracker.emitters

import java.util.concurrent.LinkedBlockingQueue

object AsyncEmitter {

  // Avoid starting thread in constructor
  def apply(): AsyncEmitter = {
    val emitter = new AsyncEmitter()
    emitter.startWorker()
    emitter
  }

}

class AsyncEmitter private extends TEmitter {

  val queue = new LinkedBlockingQueue[Map[String, String]]()

  val worker = new Thread {
    override def run {
      while (true) {
        val event = queue.take()

        println(event)
      }
    }
  }

  def input(event: Map[String, String]) {
    queue.put(event)
  }

  private def startWorker() {
    worker.start()
  }
}
