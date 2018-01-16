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
import uk.gov.hmrc.apidocumentation.models.{ApiDefinition, ApiStatus, ApiVersion, ExtendedApiDefinition}
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
    val localDef1 = ApiDefinition(serviceName, "Hello World", "Example", "hello", None, None, Seq(ApiVersion("1.0", None, ApiStatus.ALPHA, Seq.empty)))
    val localDef2 = ApiDefinition("api-person", "Hello Person", "Example", "hello-person", None, None, Seq(ApiVersion("1.0", None, ApiStatus.STABLE, Seq.empty)))
    val remoteDef = localDef1.copy(versions = Seq(ApiVersion("2.0", None, ApiStatus.BETA, Seq.empty)))
    val apiDefinitions = Seq(localDef1, localDef2)
    val apiDefinition = ExtendedApiDefinition(apiDefinitions.head, Map.empty)

    val underTest = new ApiDefinitionService(mockApiDefinitionConnector, mockApiDocumentationConnector)
    when(mockApiDocumentationConnector.fetchApiDefinitions(any[Option[String]])(any[HeaderCarrier])).thenReturn(Seq.empty)

    def theConnectorWillReturnTheApiDefinition = {
      when(mockApiDefinitionConnector.fetchApiDefinition(anyString, any[Option[String]])(any[HeaderCarrier]))
          .thenReturn(Future.successful(apiDefinition))
    }

    def theConnectorWillFailToReturnTheApiDefinition = {
      when(mockApiDefinitionConnector.fetchApiDefinition(anyString, any[Option[String]])(any[HeaderCarrier]))
          .thenReturn(Future.failed(new RuntimeException))
    }

    def theConnectorWillReturnTheApiDefinitions = {
      when(mockApiDefinitionConnector.fetchApiDefinitions(any[Option[String]])(any[HeaderCarrier]))
          .thenReturn(Future.successful(apiDefinitions))
    }

    def theConnectorWillFailToReturnTheApiDefinitions = {
      when(mockApiDefinitionConnector.fetchApiDefinitions(any[Option[String]])(any[HeaderCarrier]))
          .thenReturn(Future.failed(new RuntimeException))
    }
  }

  "fetchApiDefinition" should {

    "call the connector to fetch the API definition" in new Setup {
      theConnectorWillReturnTheApiDefinition

      await(underTest.fetchApiDefinition(serviceName, Some(loggedInUserEmail)))

      verify(mockApiDefinitionConnector).fetchApiDefinition(eqTo(serviceName), eqTo(Some(loggedInUserEmail)))(any[HeaderCarrier])
    }

    "return the API definition" in new Setup {
      theConnectorWillReturnTheApiDefinition

      val result = await(underTest.fetchApiDefinition(serviceName, Some(loggedInUserEmail)))

      result shouldBe apiDefinition
    }

    "fail when the connector fails to return the API definition" in new Setup {
      theConnectorWillFailToReturnTheApiDefinition

      intercept[RuntimeException] {
        await(underTest.fetchApiDefinition(serviceName, Some(loggedInUserEmail)))
      }
    }
  }

  "fetchApiDefinitions" should {

    "call the connector to fetch the API definitions" in new Setup {
      theConnectorWillReturnTheApiDefinitions

      await(underTest.fetchApiDefinitions(Some(loggedInUserEmail)))

      verify(mockApiDefinitionConnector).fetchApiDefinitions(eqTo(Some(loggedInUserEmail)))(any[HeaderCarrier])
    }

    "return the API definitions" in new Setup {
      theConnectorWillReturnTheApiDefinitions

      val result = await(underTest.fetchApiDefinitions(Some(loggedInUserEmail)))

      result shouldBe apiDefinitions.sortBy(_.name)
    }

    "return the API definitions keeping remote ones first" in new Setup {
      theConnectorWillReturnTheApiDefinitions
      when(mockApiDocumentationConnector.fetchApiDefinitions(any[Option[String]])(any[HeaderCarrier])).thenReturn(Seq(remoteDef))

      val result = await(underTest.fetchApiDefinitions(Some(loggedInUserEmail)))

      result shouldBe Seq(localDef2,remoteDef)
    }

    "fail when the connector fails to return the API definitions" in new Setup {
      theConnectorWillFailToReturnTheApiDefinitions

      intercept[RuntimeException] {
        await(underTest.fetchApiDefinitions(Some(loggedInUserEmail)))
      }
    }
  }
}
