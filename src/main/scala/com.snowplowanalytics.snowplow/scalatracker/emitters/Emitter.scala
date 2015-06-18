package com.snowplowanalytics.snowplow.scalatracker.emitters

import java.io.IOException
import akka.actor.{ Actor, ActorLogging, Props }
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.postfixOps
import scala.collection.mutable.{ Map => MMap }

object Emitter {
  import org.apache.commons.codec.binary.Base64

  val BACKOFF_PERIOD = 10 seconds
  val Encoding = "UTF-8"

  implicit def enrichMap[T, U](m: MMap[T, U]) = new JsonMap[T, U](m)

  implicit val encode: (Boolean, String, Pair[String, String]) => MMap[String, String] = (encodeBase64, jsonString, which) =>
    if (encodeBase64)
      MMap(which._1 -> new String(Base64.encodeBase64(jsonString.getBytes(Encoding)), Encoding))
    else
      MMap(which._2 -> jsonString)

  class JsonMap[T, U](value: MMap[T, U]) {
    def addJson(jsonString: String, encodeBase64: Boolean = true, which: Pair[T, U])(implicit encode: (Boolean, String, Pair[T, U]) => MMap[T, U]) =
      value ++= encode(encodeBase64, jsonString, which)
  }

  type Payload = MMap[String, String]

  def props(host: String, port: Int) = Props(new Emitter(host, port))
}

class Emitter(host: String, port: Int) extends Actor with ActorLogging {
  import scala.concurrent.Future
  import akka.stream.ActorFlowMaterializer
  import akka.stream.scaladsl._
  import akka.http.scaladsl.model._
  import akka.http.scaladsl.Http
  import akka.http.scaladsl.client.RequestBuilding._
  import akka.http.scaladsl.model.StatusCodes._
  import context.dispatcher
  import Emitter._
  import java.util.concurrent.TimeoutException

  implicit val system = context.system
  implicit val materializer = ActorFlowMaterializer()

  val connection: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
    Http().outgoingConnection(host, port)

  def sendRequest(request: HttpRequest): Future[HttpResponse] =
    Source.single(request).via(connection).runWith(Sink.head)

  override def receive: Receive = {
    case payload: Payload =>
      val tracker = sender
      sendRequest(Get(prepareUri("/i", payload))) flatMap { response =>
        response.status match {
          case OK =>
            tracker ! (payload, response)
            Future.successful { log.info("Successfully sent event.") }
          case _ =>
            val error = s"Failed with status code ${response.status}"
            log.error(error)
            tracker ! (payload, response)
            Future.failed(new IOException(error))
        }
      } recover {
        case t: TimeoutException =>
          log.info(s"Timed out sending request. Attempt to resend the request again")
          context.system.scheduler.scheduleOnce(BACKOFF_PERIOD, self, payload)(system.dispatcher, sender = tracker)
      }
  }

  def prepareUri(path: String, withPayload: Payload): Uri =
    Uri(scheme = "http",
      path = Uri.Path(path),
      authority = Uri.Authority(Uri.Host(host), port),
      query = Uri.Query(withPayload.toMap))

}