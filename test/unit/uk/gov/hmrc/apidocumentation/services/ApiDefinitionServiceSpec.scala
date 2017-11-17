/*
 * Copyright 2017 HM Revenue & Customs
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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apidocumentation.connectors.ApiDefinitionConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{any, anyString, eq => eqTo}
import uk.gov.hmrc.apidocumentation.models.{ApiDefinition, ExtendedApiDefinition}

import scala.concurrent.Future

class ApiDefinitionServiceSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  trait Setup {
    val apiDefinitionConnector = mock[ApiDefinitionConnector]
    implicit val hc = new HeaderCarrier()

    val serviceName = "api-example-microservice"
    val loggedInUserEmail = "john.doe@example.com"
    val apiDefinitions = Seq(ApiDefinition(serviceName, "Hello World", "Example", "hello", None, None, Seq.empty))
    val apiDefinition = ExtendedApiDefinition(apiDefinitions.head, Map.empty)

    val underTest = new ApiDefinitionService(apiDefinitionConnector)

    def theConnectorWillReturnTheApiDefinition = {
      when(apiDefinitionConnector.fetchApiDefinition(anyString, any[Option[String]])(any[HeaderCarrier]))
          .thenReturn(Future.successful(apiDefinition))
    }

    def theConnectorWillFailToReturnTheApiDefinition = {
      when(apiDefinitionConnector.fetchApiDefinition(anyString, any[Option[String]])(any[HeaderCarrier]))
          .thenReturn(Future.failed(new RuntimeException))
    }

    def theConnectorWillReturnTheApiDefinitions = {
      when(apiDefinitionConnector.fetchApiDefinitions(any[Option[String]])(any[HeaderCarrier]))
          .thenReturn(Future.successful(apiDefinitions))
    }

    def theConnectorWillFailToReturnTheApiDefinitions = {
      when(apiDefinitionConnector.fetchApiDefinitions(any[Option[String]])(any[HeaderCarrier]))
          .thenReturn(Future.failed(new RuntimeException))
    }
  }

  "fetchApiDefinition" should {

    "call the connector to fetch the API definition" in new Setup {
      theConnectorWillReturnTheApiDefinition

      await(underTest.fetchApiDefinition(serviceName, Some(loggedInUserEmail)))

      verify(apiDefinitionConnector).fetchApiDefinition(eqTo(serviceName), eqTo(Some(loggedInUserEmail)))(any[HeaderCarrier])
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

      verify(apiDefinitionConnector).fetchApiDefinitions(eqTo(Some(loggedInUserEmail)))(any[HeaderCarrier])
    }

    "return the API definitions" in new Setup {
      theConnectorWillReturnTheApiDefinitions

      val result = await(underTest.fetchApiDefinitions(Some(loggedInUserEmail)))

      result shouldBe apiDefinitions
    }

    "fail when the connector fails to return the API definitions" in new Setup {
      theConnectorWillFailToReturnTheApiDefinitions

      intercept[RuntimeException] {
        await(underTest.fetchApiDefinitions(Some(loggedInUserEmail)))
      }
    }
  }
}
