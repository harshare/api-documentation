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
import org.mockito.ArgumentMatchers.{any, anyString, eq => eqTo}
import org.mockito.Mockito.{verify, verifyZeroInteractions, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status
import play.api.libs.ws.{DefaultWSResponseHeaders, StreamedResponse}
import uk.gov.hmrc.apidocumentation.config.ServiceConfiguration
import uk.gov.hmrc.apidocumentation.connectors.{ApiDocumentationConnector, ApiMicroserviceConnector}
import uk.gov.hmrc.apidocumentation.models.{ApiAccess, ApiAccessType, ApiAvailability, ApiStatus, ExtendedApiDefinition, ExtendedApiVersion}
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException, NotFoundException}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class DocumentationServiceSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  val serviceName = "hello-world"
  val version = "1.0"
  val productionV1Availability = ApiAvailability(endpointsEnabled = true, ApiAccess(ApiAccessType.PRIVATE, Some(Seq.empty)), loggedIn = false, authorised = false)
  val productionV2Availability = ApiAvailability(endpointsEnabled = true, ApiAccess(ApiAccessType.PRIVATE, Some(Seq.empty)), loggedIn = false, authorised = false)
  val sandboxV2Availability = ApiAvailability(endpointsEnabled = true, ApiAccess(ApiAccessType.PUBLIC, Some(Seq.empty)), loggedIn = false, authorised = false)
  val sandboxV3Availability = ApiAvailability(endpointsEnabled = false, ApiAccess(ApiAccessType.PUBLIC, Some(Seq.empty)), loggedIn = false, authorised = false)
  val apiDefinition = ExtendedApiDefinition(serviceName, "http://hello.protected.mdtp", "Hello World", "Example", "hello",
    requiresTrust = false, isTestSupport = false, Seq(
      ExtendedApiVersion("1.0", ApiStatus.STABLE, Seq.empty, Some(productionV1Availability), None),
      ExtendedApiVersion("2.0", ApiStatus.BETA, Seq.empty, Some(productionV2Availability), Some(sandboxV2Availability)),
      ExtendedApiVersion("3.0", ApiStatus.ALPHA, Seq.empty, None, Some(sandboxV3Availability))
    ))
  val file = new java.io.File("hello")
  val path: java.nio.file.Path = file.toPath
  val source: Source[ByteString, _] = FileIO.fromPath(path)
  val streamedResource = StreamedResponse(DefaultWSResponseHeaders(Status.OK, Map("Content-Type" -> Seq("application/text"))), source)
  val chunkedResource = StreamedResponse(DefaultWSResponseHeaders(Status.OK, Map.empty), source)
  val notFoundResource = StreamedResponse(DefaultWSResponseHeaders(Status.NOT_FOUND, Map.empty), source)
  val internalServerErrorResource = StreamedResponse(DefaultWSResponseHeaders(Status.INTERNAL_SERVER_ERROR, Map.empty), source)

  trait Setup {
    implicit val hc = HeaderCarrier()

    val mockApiDefinitionService = mock[ApiDefinitionService]
    val mockApiMicroserviceConnector = mock[ApiMicroserviceConnector]
    val mockApiDocumentationConnector = mock[ApiDocumentationConnector]
    val mockServiceConfig = mock[ServiceConfiguration]

    val underTest = new DocumentationService(mockApiDefinitionService, mockApiMicroserviceConnector,
      mockApiDocumentationConnector, mockServiceConfig)

    def theServiceIsRunningInSandboxMode = when(mockServiceConfig.isSandbox).thenReturn(true)

    def theApiDefinitionWillBeReturned = {
      when(mockApiDefinitionService.fetchApiDefinition(anyString, any[Option[String]])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(apiDefinition)))
    }

    def noApiDefinitionWillBeReturned = {
      when(mockApiDefinitionService.fetchApiDefinition(anyString, any[Option[String]])(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
    }

    def theApiMicroserviceWillReturnTheResource(response: StreamedResponse) = {
      when(mockApiMicroserviceConnector.fetchApiDocumentationResource(anyString, anyString, anyString)(any[HeaderCarrier]))
        .thenReturn(Future.successful(response))
    }

    def theApiDocumentationServiceWillReturnTheResource(response: StreamedResponse) = {
      when(mockApiDocumentationConnector.fetchApiDocumentationResource(anyString, anyString, anyString)(any[HeaderCarrier]))
        .thenReturn(Future.successful(response))
    }

    def theApiDocumentationServiceWillFail() = {
      when(mockApiDocumentationConnector.fetchApiDocumentationResource(anyString, anyString, anyString)(any[HeaderCarrier]))
        .thenReturn(Future.failed(new RuntimeException))
    }
  }

  "DocumentationService" should {

    "return the resource fetched from local microservice when the API version exists in production only" in new Setup {
      theApiDefinitionWillBeReturned
      theApiMicroserviceWillReturnTheResource(streamedResource)

      val result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resource")(hc))

      result.header.status should be(Status.OK)
      verify(mockApiDefinitionService).fetchApiDefinition(eqTo(serviceName), any[Option[String]])(any[HeaderCarrier])
      verify(mockApiMicroserviceConnector).fetchApiDocumentationResource(eqTo(serviceName), eqTo("1.0"), eqTo("resource"))(any[HeaderCarrier])
      verifyZeroInteractions(mockApiDocumentationConnector)
    }

    "return the resource fetched from remote API documentation service when the API version exists in sandbox and production" in new Setup {
      theApiDefinitionWillBeReturned
      theApiDocumentationServiceWillReturnTheResource(streamedResource)

      val result = await(underTest.fetchApiDocumentationResource(serviceName, "2.0", "resource")(hc))

      result.header.status should be(Status.OK)
      verify(mockApiDefinitionService).fetchApiDefinition(eqTo(serviceName), any[Option[String]])(any[HeaderCarrier])
      verify(mockApiDocumentationConnector).fetchApiDocumentationResource(eqTo(serviceName), eqTo("2.0"), eqTo("resource"))(any[HeaderCarrier])
      verifyZeroInteractions(mockApiMicroserviceConnector)
    }

    "return the resource fetched from local API documentation service when the API version exists in sandbox and production but remote service returns not found" in new Setup {
      theApiDefinitionWillBeReturned
      theApiDocumentationServiceWillReturnTheResource(notFoundResource)
      theApiMicroserviceWillReturnTheResource(streamedResource)

      val result = await(underTest.fetchApiDocumentationResource(serviceName, "2.0", "resource")(hc))

      result.header.status should be(Status.OK)
      verify(mockApiDefinitionService).fetchApiDefinition(eqTo(serviceName), any[Option[String]])(any[HeaderCarrier])
      verify(mockApiDocumentationConnector).fetchApiDocumentationResource(eqTo(serviceName), eqTo("2.0"), eqTo("resource"))(any[HeaderCarrier])
      verify(mockApiMicroserviceConnector).fetchApiDocumentationResource(eqTo(serviceName), eqTo("2.0"), eqTo("resource"))(any[HeaderCarrier])
    }

    "return the resource fetched from local API documentation service when the API version exists in sandbox and production but remote service returns error" in new Setup {
      theApiDefinitionWillBeReturned
      theApiDocumentationServiceWillReturnTheResource(internalServerErrorResource)
      theApiMicroserviceWillReturnTheResource(streamedResource)

      val result = await(underTest.fetchApiDocumentationResource(serviceName, "2.0", "resource")(hc))

      result.header.status should be(Status.OK)
      verify(mockApiDefinitionService).fetchApiDefinition(eqTo(serviceName), any[Option[String]])(any[HeaderCarrier])
      verify(mockApiDocumentationConnector).fetchApiDocumentationResource(eqTo(serviceName), eqTo("2.0"), eqTo("resource"))(any[HeaderCarrier])
      verify(mockApiMicroserviceConnector).fetchApiDocumentationResource(eqTo(serviceName), eqTo("2.0"), eqTo("resource"))(any[HeaderCarrier])
    }

    "return the resource fetched from local API documentation service when the API version exists in sandbox and production but remote service fails" in new Setup {
      theApiDefinitionWillBeReturned
      theApiDocumentationServiceWillFail()
      theApiMicroserviceWillReturnTheResource(streamedResource)

      val result = await(underTest.fetchApiDocumentationResource(serviceName, "2.0", "resource")(hc))

      result.header.status should be(Status.OK)
      verify(mockApiDefinitionService).fetchApiDefinition(eqTo(serviceName), any[Option[String]])(any[HeaderCarrier])
      verify(mockApiDocumentationConnector).fetchApiDocumentationResource(eqTo(serviceName), eqTo("2.0"), eqTo("resource"))(any[HeaderCarrier])
      verify(mockApiMicroserviceConnector).fetchApiDocumentationResource(eqTo(serviceName), eqTo("2.0"), eqTo("resource"))(any[HeaderCarrier])
    }

    "return the resource fetched from remote API documentation service when the API version exists in sandbox only" in new Setup {
      theApiDefinitionWillBeReturned
      theApiDocumentationServiceWillReturnTheResource(streamedResource)

      val result = await(underTest.fetchApiDocumentationResource(serviceName, "3.0", "resource")(hc))

      result.header.status should be(Status.OK)
      verify(mockApiDefinitionService).fetchApiDefinition(eqTo(serviceName), any[Option[String]])(any[HeaderCarrier])
      verify(mockApiDocumentationConnector).fetchApiDocumentationResource(eqTo(serviceName), eqTo("3.0"), eqTo("resource"))(any[HeaderCarrier])
      verifyZeroInteractions(mockApiMicroserviceConnector)
    }

    "return the resource fetched from local API microservice when the API version exists in sandbox only and isSandbox flag is set" in new Setup {
      theServiceIsRunningInSandboxMode
      theApiDefinitionWillBeReturned
      theApiMicroserviceWillReturnTheResource(streamedResource)

      val result = await(underTest.fetchApiDocumentationResource(serviceName, "3.0", "resource")(hc))

      result.header.status should be(Status.OK)
      verify(mockApiDefinitionService).fetchApiDefinition(eqTo(serviceName), any[Option[String]])(any[HeaderCarrier])
      verify(mockApiMicroserviceConnector).fetchApiDocumentationResource(eqTo(serviceName), eqTo("3.0"), eqTo("resource"))(any[HeaderCarrier])
      verifyZeroInteractions(mockApiDocumentationConnector)
    }

    "return the resource with given Content-Type when header is present" in new Setup {
      theApiDefinitionWillBeReturned
      theApiMicroserviceWillReturnTheResource(streamedResource)

      val result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resource")(hc))

      result.header.status should be(Status.OK)
      result.body.contentType should be(Some("application/text"))
    }

    "return the resource with default Content-Type when header is not present" in new Setup {
      theApiDefinitionWillBeReturned
      theApiMicroserviceWillReturnTheResource(chunkedResource)

      val result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resource")(hc))

      result.header.status should be(Status.OK)
      result.body.contentType should be(Some("application/octet-stream"))
    }

    "fail when resource not found from API Documentation service when in production only" in new Setup {
      theApiDefinitionWillBeReturned
      theApiDocumentationServiceWillReturnTheResource(notFoundResource)

      intercept[NotFoundException] {
        await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resourceNotThere")(hc))
      }
    }

    "fail when resource not found from local API microservice" in new Setup {
      theApiDefinitionWillBeReturned
      theApiMicroserviceWillReturnTheResource(notFoundResource)

      intercept[NotFoundException] {
        await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resourceNotThere")(hc))
      }
    }

    "fail when API Documentation service returns an internal server error when in production only" in new Setup {
      theApiDefinitionWillBeReturned
      theApiDocumentationServiceWillReturnTheResource(internalServerErrorResource)

      intercept[InternalServerException] {
        await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resourceNotThere")(hc))
      }
    }

    "fail when local API microservice returns an internal server error" in new Setup {
      theApiDefinitionWillBeReturned
      theApiMicroserviceWillReturnTheResource(internalServerErrorResource)

      intercept[InternalServerException] {
        await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resourceNotThere")(hc))
      }
    }

    "fail when API definition is not found" in new Setup {
      noApiDefinitionWillBeReturned

      intercept[IllegalArgumentException] {
        await(underTest.fetchApiDocumentationResource(serviceName, "4.0", "resource")(hc))
      }
    }

    "fail when API version is not found" in new Setup {
      theApiDefinitionWillBeReturned

      intercept[IllegalArgumentException] {
        await(underTest.fetchApiDocumentationResource(serviceName, "4.0", "resource")(hc))
      }
    }
  }
}
