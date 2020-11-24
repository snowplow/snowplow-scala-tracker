/*
 * Copyright (c) 2015-2020 Snowplow Analytics Ltd. All rights reserved.
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

import java.net.{SocketTimeoutException, UnknownHostException}
import java.util.concurrent.Executors

import cats.Id
import cats.effect.{ContextShift, IO}
import com.snowplowanalytics.snowplow.scalatracker.{Emitter, Payload}
import org.specs2.Specification
import scala.concurrent.ExecutionContext
import scalaj.http.HttpResponse

class MetadataSpec extends Specification {

  // Use enough threads to support the Thread.sleep calls, and still allow concurrent timeouts.
  val ec: ExecutionContext                    = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5))
  implicit def contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer                          = IO.timer(ec)

  val ec2Response = """
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
    """.stripMargin

  val gceResponse = """{ "foo": "bar" }"""

  def is = s2"""

    ec2 method should make a request and return json $e1
    gce method should make a request and return json $e2

    ec2 method should make a request and throw an exception $e3
    gce method should make a request and throw an exception $e4

    ec2 timeout method must work correctly                            $e5
    gce timeout method must work correctly                            $e6

    """

  val emitter: Emitter[Id] = new Emitter[Id] {
    override def send(event: Payload): Unit = ()
    override def flushBuffer(): Unit        = ()
  }

  def e1 = {
    val client: HttpClient = { _ =>
      new HttpResponse(ec2Response, 200, Map.empty)
    }

    new Ec2Metadata[IO](client).getInstanceContext
      .unsafeRunSync()
      .schema
      .vendor must beEqualTo("com.amazon.aws.ec2")
  }

  def e2 = {
    val client: HttpClient = { _ =>
      new HttpResponse(gceResponse, 200, Map.empty)
    }

    new GceMetadata[IO](client).getInstanceContext
      .unsafeRunSync()
      .schema
      .vendor must beEqualTo("com.google.cloud.gce")
  }

  def e3 = {
    val client: HttpClient = { _ =>
      throw new SocketTimeoutException()
    }
    new Ec2Metadata[IO](client).getInstanceContext
      .unsafeRunSync() must throwA[SocketTimeoutException]
  }

  def e4 = {
    val client: HttpClient = { _ =>
      throw new UnknownHostException()
    }
    new GceMetadata[IO](client).getInstanceContext
      .unsafeRunSync() must throwA[UnknownHostException]
  }

  def e5 = {

    val client: HttpClient = { _ =>
      Thread.sleep(5000)
      new HttpResponse(ec2Response, 200, Map.empty)
    }

    new Ec2Metadata[IO](client).getInstanceContextBlocking
      .unsafeRunSync() must beNone
  }

  def e6 = {
    val client: HttpClient = { _ =>
      Thread.sleep(5000)
      new HttpResponse(gceResponse, 200, Map.empty)
    }

    new GceMetadata[IO](client).getInstanceContextBlocking
      .unsafeRunSync() must beNone
  }

}
