package com.snowplowanalytics.snowplow.scalatracker.emitters

import akka.actor.{ Actor, ActorLogging, Props }
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.postfixOps

object Emitter {
  val BACKOFF_PERIOD = 10 seconds

  implicit def enrichMap[T, U](m: Map[T, U]) = new JsonMap[T, U](m)

  class JsonMap[T, U](value: Map[T, U]) {
    def addJson(jsonString: String, encodeBase64: Boolean = true, encode: (Boolean, String) => Map[T, U]) =
      value ++ encode(encodeBase64, jsonString)
  }

  type Payload = Map[String, String]

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

  implicit val system = context.system
  implicit val materializer = ActorFlowMaterializer()

  val connection: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
    Http().outgoingConnection(host, port)

  def sendRequest(request: HttpRequest): Future[HttpResponse] =
    Source.single(request).via(connection).runWith(Sink.head)

  override def receive: Receive = {
    case payload: Payload =>
      sendRequest(Get(prepareUri("/i", payload))) flatMap { response =>
        response.status match {
          case OK => Future.successful { log.info("Successfully sent event.") }
          case _ => throw new Error("Recover this failure")
        }
      } recover {
        case _ =>
          // try again until successful
          context.system.scheduler.scheduleOnce(BACKOFF_PERIOD, self, payload)
      }
  }

  private def prepareUri(path: String, withPayload: Payload): Uri =
    Uri(scheme = "http",
      path = Uri.Path(path),
      authority = Uri.Authority(Uri.Host(host), port),
      query = Uri.Query(withPayload))

}