package com.snowplowanalytics.snowplow.scalatracker
package emitters.pubsub

import java.util.concurrent.Executor

import com.google.api.core.{ApiFuture, ApiFutureCallback, ApiFutures}
import com.google.protobuf.ByteString
import com.google.cloud.pubsub.v1.Publisher
import com.google.pubsub.v1.{ ProjectTopicName, PubsubMessage }

import io.circe.Json

import cats.effect.{IO, Timer}
import cats.syntax.functor._
import cats.syntax.either._

class PubSubEmitter(publisher: Publisher, projectTopicName: ProjectTopicName)(implicit timer: Timer[IO])
    extends Emitter[IO] {

  def send(event: Emitter.Payload): IO[Unit] = {
    val payload       = Json.fromFields(event.mapValues(Json.fromString).toList).noSpaces
    val data          = ByteString.copyFromUtf8(payload)
    val pubsubMessage = PubsubMessage.newBuilder.setData(data).build

    PubSubEmitter.toIO(publisher.publish(pubsubMessage), ???).void
  }
}

object PubSubEmitter {
  def create(topic: String)(implicit timer: Timer[IO]): Either[String, PubSubEmitter] =
    topic.split("/").toList match {
      case List("projects", projectId: String, "topics", topicName: String) =>
        val topic     = ProjectTopicName.of(projectId, topicName)
        val publisher = Publisher.newBuilder(topicName).build
        new PubSubEmitter(publisher, topic).asRight
      case _ =>
        s"Topic format of $topic is wrong. Expected projects/PROJECT_ID/topics/TOPIC_NAME".asLeft
    }

  def toIO[A](future: => ApiFuture[A], executor: Executor): IO[A] =
    IO.async[A] { callback =>
      ApiFutures.addCallback(future, new ApiFutureCallback[A] {
        def onFailure(t: Throwable): Unit =
          callback(Left(t))
        def onSuccess(result: A): Unit =
          callback(Right(result))
      }, executor)
    }
}
