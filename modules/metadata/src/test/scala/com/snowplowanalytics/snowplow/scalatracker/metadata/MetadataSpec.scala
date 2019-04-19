/*
 * Copyright (c) 2015-2018 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.scalatracker.metadata

import java.util.UUID

import scala.concurrent.duration._
import cats.Id
import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO, Timer}

import com.snowplowanalytics.snowplow.scalatracker.{Emitter, Tracker, UUIDProvider}
import com.snowplowanalytics.snowplow.scalatracker.Emitter.EmitterPayload

import org.specs2.Specification
import org.specs2.mock.Mockito

class MetadataSpec extends Specification with Mockito {
  import scala.concurrent.ExecutionContext.Implicits.global

  import com.snowplowanalytics.snowplow.scalatracker.syntax.id._

  implicit val timer = IO.timer(global)

  val ec2Response = IO.pure("""
      |{
      |    "devpayProductCodes" : null,
      |    "marketplaceProductCodes" : [ "1abc2defghijklm3nopqrs4tu" ],
      |    "availabilityZone" : "us-west-2b",
      |    "privateIp" : "10.158.112.84",
      |    "version" : "2017-09-30",
      |    "instanceId" : "i-1234567890abcdef0",
      |    "billingProducts" : null,
      |    "instanceType" : "t2.micro",
      |    "accountId" : "123456789012",
      |    "imageId" : "ami-5fb8c835",
      |    "pendingTime" : "2016-11-19T16:32:11Z",
      |    "architecture" : "x86_64",
      |    "kernelId" : null,
      |    "ramdiskId" : null,
      |    "region" : "us-west-2"
      |}
    """.stripMargin)

  val gceResponse = IO.pure("""{ "foo": "bar" }""")

  val ec2Spy =
    spy(new Ec2Metadata[IO]).getContent(anyString).returns(ec2Response).getMock[Ec2Metadata[IO]]
  val gceSpy =
    spy(new GceMetadata[IO]).getString(anyString).returns(gceResponse).getMock[GceMetadata[IO]]

  def is = s2"""

    ec2 extension method should make a request and throw an exception $e1
    gce extension method should make a request and throw an exception e2

    ec2 timeout method must work correctly                            $e3
    gce timeout method must work correctly                            $e4

    """

  val emitter: Emitter[Id] = new Emitter[Id] {
    override def send(event: EmitterPayload): Id[Unit] = ()
  }

  implicit val contextShift: ContextShift[IO] = IO.contextShift(global)

  def e1 =
    Tracker(NonEmptyList.of(emitter), "foo", "foo")
      .enableEc2Context[IO]
      .unsafeRunSync() must throwA[Throwable]

  def e2 =
    Tracker(NonEmptyList.of(emitter), "foo", "foo")
      .enableGceContext[IO]
      .timeout(1.second)
      .unsafeRunSync() must throwA[Throwable]

  def e3 = {
    ec2Spy.getInstanceContextBlocking.unsafeRunSync() must beSome

    val blockingInstance = spy(new Ec2Metadata[IO])
      .getContent(anyString)
      .returns(IO.sleep(5.seconds).map(_ => "foo"))
      .getMock[Ec2Metadata[IO]]

    blockingInstance.getInstanceContextBlocking.unsafeRunSync must beNone
  }

  def e4 = {
    gceSpy.getInstanceContextBlocking.unsafeRunSync() must beSome

    val blockingInstance = spy(new GceMetadata[IO])
      .getString(anyString)
      .returns(IO.sleep(5.seconds).map(_ => "foo"))
      .getMock[GceMetadata[IO]]

    blockingInstance.getInstanceContextBlocking.unsafeRunSync must beNone
  }

}
