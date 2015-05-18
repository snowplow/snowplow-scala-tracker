package com.snowplowanalytics.snowplow.scalatracker.emitters

import java.util.concurrent.LinkedBlockingQueue

object AsyncEmitter {

  // Avoid starting thread in constructor
  def apply(host: String): AsyncEmitter = {
    val emitter = new AsyncEmitter(host)
    emitter.startWorker()
    emitter
  }

}

class AsyncEmitter private(host: String) extends TEmitter {

  val queue = new LinkedBlockingQueue[Map[String, String]]()

  val worker = new Thread {
    override def run {
      while (true) {
        val event = queue.take()

        TEmitter.attemptGet(host, event)
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
