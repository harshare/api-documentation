/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.apidocumentation.services

import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import mockws.MockWS
import org.mockito.ArgumentMatchers.{any, anyString, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.HttpEntity
import play.api.mvc.{Action, ResponseHeader, Result, Results}
import uk.gov.hmrc.apidocumentation.connectors.ServiceLocatorConnector
import uk.gov.hmrc.apidocumentation.models.ServiceDetails
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException, NotFoundException, Upstream4xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class DocumentationServiceSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  val serviceUrl = "http://api-example-microservice.protected.mdtp"

  val serviceName = "hello-world"
  val version = "1.0"
  val resourceFoundUrl = s"$serviceUrl/api/conf/$version/resource"
  val streamedResourceUrl = s"$serviceUrl/api/conf/$version/streamedResource"
  val resourceNotFoundUrl = s"$serviceUrl/api/conf/$version/resourceNotThere"
  val serviceUnavailableUrl = s"$serviceUrl/api/conf/$version/resourceInvalid"
  val timeoutUrl = s"$serviceUrl/api/conf/$version/timeout"
  val serviceLocatorConnector = mock[ServiceLocatorConnector]

  val file = new java.io.File("hello")
  val path: java.nio.file.Path = file.toPath
  val source: Source[ByteString, _] = FileIO.fromPath(path)
  val mockWS = MockWS {
    case ("GET", `resourceFoundUrl`) => Action(Results.Ok("hello world"))
    case ("GET", `streamedResourceUrl`) => Action(Result(
      header = ResponseHeader(200, Map("Content-length"-> s"${file.length()}")),
      body = HttpEntity.Streamed(source, Some(file.length()), Some("application/pdf"))
    ))
    case ("GET", `resourceNotFoundUrl`) => Action(Results.NotFound)
    case ("GET", `serviceUnavailableUrl`) => Action(Results.ServiceUnavailable)
    case ("GET", `timeoutUrl`) => Action(Results.RequestTimeout)
  }

  trait Setup {
    implicit val hc = HeaderCarrier()
    val underTest = new DocumentationService(serviceLocatorConnector, mockWS)
    def serviceLocatorWillReturnTheServiceDetails = {
      when(serviceLocatorConnector.lookupService(anyString)(any[HeaderCarrier]))
        .thenReturn(Future.successful(ServiceDetails(serviceName, serviceUrl)))
    }

    def serviceLocatorWillFailToReturnTheServiceDetails = {
      when(serviceLocatorConnector.lookupService(anyString)(any[HeaderCarrier]))
        .thenReturn(Future.failed(Upstream4xxResponse("Not found", 404, 404)))
    }

  }

  "DocumentationService" should {

    "ask service locator for the service URL" in new Setup {
      serviceLocatorWillReturnTheServiceDetails

      val result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resource")(hc))

      verify(serviceLocatorConnector).lookupService(eqTo(serviceName))(any[HeaderCarrier])
    }

    "return the resource" in new Setup {
      serviceLocatorWillReturnTheServiceDetails

      val result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resource")(hc))

      result.header.status should be(200)

    }

    "fail when service locator does not find the service URL" in new Setup {
      serviceLocatorWillFailToReturnTheServiceDetails

      intercept[Upstream4xxResponse] {
        await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resourceNotThere")(hc))
      }
    }

    "fail when resource not found" in new Setup {
      serviceLocatorWillReturnTheServiceDetails

      intercept[NotFoundException] {
        await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resourceNotThere")(hc))
      }
    }

    "return streamed resource" in new Setup {
      serviceLocatorWillReturnTheServiceDetails
      val result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "streamedResource")(hc))

      result.header.status should be(200)
    }

    "fail when the service does not return the resource" in new Setup {
      serviceLocatorWillReturnTheServiceDetails

      intercept[InternalServerException] {
        await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resourceInvalid")(hc))
      }
    }
  }
}
