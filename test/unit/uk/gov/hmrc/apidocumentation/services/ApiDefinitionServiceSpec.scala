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

import org.mockito.ArgumentMatchers.{any, anyString, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apidocumentation.connectors.{ApiDefinitionConnector, ApiDocumentationConnector}
import uk.gov.hmrc.apidocumentation.models.{ApiAccess, ApiAccessType, ApiAvailability, ApiDefinition, ApiStatus, ApiVersion, ExtendedApiDefinition, ExtendedApiVersion}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class ApiDefinitionServiceSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  trait Setup {
    val mockApiDefinitionConnector = mock[ApiDefinitionConnector]
    implicit val hc = new HeaderCarrier()
    val mockApiDocumentationConnector = mock[ApiDocumentationConnector]
    val serviceName = "api-example-microservice"
    val loggedInUserEmail = "john.doe@example.com"
    val localDef1 = ApiDefinition(serviceName, "Hello World", "Example", "hello", None, None, Seq(ApiVersion("1.0", None, ApiStatus.ALPHA, Seq.empty, false)))
    val localDef2 = ApiDefinition("api-person", "Hello Person", "Example", "hello-person", None, None, Seq(ApiVersion("1.0", None, ApiStatus.STABLE, Seq.empty, false)))
    val remoteDef = localDef1.copy(versions = Seq(ApiVersion("2.0", None, ApiStatus.BETA, Seq.empty, false)))
    val apiDefinitions = Seq(localDef1, localDef2)

    val productionV1Availability = ApiAvailability(endpointsEnabled = true, ApiAccess(ApiAccessType.PRIVATE), loggedIn = false, authorised = false)
    val sandboxV1Availability = ApiAvailability(endpointsEnabled = true, ApiAccess(ApiAccessType.PUBLIC), loggedIn = false, authorised = false)
    val sandboxV2Availability = ApiAvailability(endpointsEnabled = false, ApiAccess(ApiAccessType.PUBLIC), loggedIn = false, authorised = false)

    val productionApiDefinition = ExtendedApiDefinition(serviceName, "http://hello.protected.mdtp", "Hello World", "Example", "hello",
      requiresTrust = false, isTestSupport = false, Seq(
        ExtendedApiVersion("1.0", ApiStatus.STABLE, Seq.empty, Some(productionV1Availability), None)
      ))

    val sandboxApiDefinition = ExtendedApiDefinition(serviceName, "http://hello.protected.mdtp", "Hello World", "Example", "hello",
      requiresTrust = false, isTestSupport = false, Seq(
        ExtendedApiVersion("1.0", ApiStatus.STABLE, Seq.empty, None, Some(sandboxV1Availability)),
        ExtendedApiVersion("2.0", ApiStatus.ALPHA, Seq.empty, None, Some(sandboxV2Availability))
      ))

    val combinedApiDefinition = ExtendedApiDefinition(serviceName, "http://hello.protected.mdtp", "Hello World", "Example", "hello",
      requiresTrust = false, isTestSupport = false, Seq(
        ExtendedApiVersion("1.0", ApiStatus.STABLE, Seq.empty, Some(productionV1Availability), Some(sandboxV1Availability)),
        ExtendedApiVersion("2.0", ApiStatus.ALPHA, Seq.empty, None, Some(sandboxV2Availability))
      ))

    val underTest = new ApiDefinitionService(mockApiDefinitionConnector, mockApiDocumentationConnector)
    when(mockApiDocumentationConnector.fetchApiDefinitions(any[Option[String]])(any[HeaderCarrier])).thenReturn(Seq.empty)

    def theApiDefinitionConnectorWillReturnTheApiDefinition = {
      when(mockApiDefinitionConnector.fetchApiDefinition(anyString, any[Option[String]])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(productionApiDefinition)))
    }

    def theApiDocumentationConnectorWillReturnTheApiDefinition = {
      when(mockApiDocumentationConnector.fetchApiDefinition(anyString, any[Option[String]])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(sandboxApiDefinition)))
    }

    def theApiDefinitionConnectorWillReturnNoApiDefinition = {
      when(mockApiDefinitionConnector.fetchApiDefinition(anyString, any[Option[String]])(any[HeaderCarrier]))
          .thenReturn(Future.successful(None))
    }

    def theApiDocumentationConnectorWillReturnNoApiDefinition = {
      when(mockApiDocumentationConnector.fetchApiDefinition(anyString, any[Option[String]])(any[HeaderCarrier]))
          .thenReturn(Future.successful(None))
    }

    def theApiDefinitionConnectorWillFailToReturnTheApiDefinition = {
      when(mockApiDefinitionConnector.fetchApiDefinition(anyString, any[Option[String]])(any[HeaderCarrier]))
          .thenReturn(Future.failed(new RuntimeException))
    }

    def theApiDocumentationConnectorWillFailToReturnTheApiDefinition = {
      when(mockApiDocumentationConnector.fetchApiDefinition(anyString, any[Option[String]])(any[HeaderCarrier]))
          .thenReturn(Future.failed(new RuntimeException))
    }

    def theApiDefinitionConnectorWillReturnTheApiDefinitions = {
      when(mockApiDefinitionConnector.fetchApiDefinitions(any[Option[String]])(any[HeaderCarrier]))
          .thenReturn(Future.successful(apiDefinitions))
    }

    def theApiDefinitionConnectorWillFailToReturnTheApiDefinitions = {
      when(mockApiDefinitionConnector.fetchApiDefinitions(any[Option[String]])(any[HeaderCarrier]))
          .thenReturn(Future.failed(new RuntimeException))
    }
  }

  "fetchApiDefinition" should {

    "call the connectors to fetch the API definition" in new Setup {
      theApiDefinitionConnectorWillReturnTheApiDefinition
      theApiDocumentationConnectorWillReturnTheApiDefinition

      await(underTest.fetchApiDefinition(serviceName, Some(loggedInUserEmail)))

      verify(mockApiDefinitionConnector).fetchApiDefinition(eqTo(serviceName), eqTo(Some(loggedInUserEmail)))(any[HeaderCarrier])
      verify(mockApiDocumentationConnector).fetchApiDefinition(eqTo(serviceName), eqTo(Some(loggedInUserEmail)))(any[HeaderCarrier])
    }

    "return the API definition when it exists in prod only" in new Setup {
      theApiDefinitionConnectorWillReturnTheApiDefinition
      theApiDocumentationConnectorWillReturnNoApiDefinition

      val result = await(underTest.fetchApiDefinition(serviceName, Some(loggedInUserEmail)))

      result shouldBe productionApiDefinition
    }

    "return the API definition when it exists in sandbox only" in new Setup {
      theApiDefinitionConnectorWillReturnNoApiDefinition
      theApiDocumentationConnectorWillReturnTheApiDefinition

      val result = await(underTest.fetchApiDefinition(serviceName, Some(loggedInUserEmail)))

      result shouldBe sandboxApiDefinition
    }

    "return the API combined definition when it exists in prod and sandbox" in new Setup {
      theApiDefinitionConnectorWillReturnTheApiDefinition
      theApiDocumentationConnectorWillReturnTheApiDefinition

      val result = await(underTest.fetchApiDefinition(serviceName, Some(loggedInUserEmail)))

      result shouldBe combinedApiDefinition
    }

    "fail when the API definition connector fails to return the API definition" in new Setup {
      theApiDefinitionConnectorWillFailToReturnTheApiDefinition
      theApiDocumentationConnectorWillReturnTheApiDefinition

      intercept[RuntimeException] {
        await(underTest.fetchApiDefinition(serviceName, Some(loggedInUserEmail)))
      }
    }
  }

  "fetchApiDefinitions" should {

    "call the connector to fetch the API definitions" in new Setup {
      theApiDefinitionConnectorWillReturnTheApiDefinitions

      await(underTest.fetchApiDefinitions(Some(loggedInUserEmail)))

      verify(mockApiDefinitionConnector).fetchApiDefinitions(eqTo(Some(loggedInUserEmail)))(any[HeaderCarrier])
    }

    "return the API definitions" in new Setup {
      theApiDefinitionConnectorWillReturnTheApiDefinitions

      val result = await(underTest.fetchApiDefinitions(Some(loggedInUserEmail)))

      result shouldBe apiDefinitions.sortBy(_.name)
    }

    "return the API definitions keeping remote ones first" in new Setup {
      theApiDefinitionConnectorWillReturnTheApiDefinitions
      when(mockApiDocumentationConnector.fetchApiDefinitions(any[Option[String]])(any[HeaderCarrier])).thenReturn(Seq(remoteDef))

      val result = await(underTest.fetchApiDefinitions(Some(loggedInUserEmail)))

      result shouldBe Seq(localDef2,remoteDef)
    }

    "fail when the connector fails to return the API definitions" in new Setup {
      theApiDefinitionConnectorWillFailToReturnTheApiDefinitions

      intercept[RuntimeException] {
        await(underTest.fetchApiDefinitions(Some(loggedInUserEmail)))
      }
    }
  }
}
