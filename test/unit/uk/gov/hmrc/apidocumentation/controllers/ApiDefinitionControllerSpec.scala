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

package uk.gov.hmrc.apidocumentation.controllers

import org.mockito.ArgumentMatchers.{any, anyString, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeRequest
import uk.gov.hmrc.apidocumentation.models.{ApiDefinition, ExtendedApiDefinition}
import uk.gov.hmrc.apidocumentation.services.ApiDefinitionService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import play.api.http.Status._
import scala.concurrent.Future

class ApiDefinitionControllerSpec  extends UnitSpec with ScalaFutures with MockitoSugar with WithFakeApplication {

  trait Setup {
    val serviceName = "api-example-microservice"
    val loggedInUserEmail = "john.doe@example.com"

    val apiDefinitions = Seq(ApiDefinition(serviceName, "Hello World", "Example", "hello", None, None, Seq.empty))
    val apiDefinition = ExtendedApiDefinition(apiDefinitions.head, Map.empty)

    implicit val mat = fakeApplication.materializer
    val apiDefinitionService = mock[ApiDefinitionService]
    val hc = new HeaderCarrier()
    val request = FakeRequest()
    val requestWithEmailQueryParameter = FakeRequest("GET", s"?email=$loggedInUserEmail")

    val underTest = new ApiDefinitionController(apiDefinitionService)

    def theServiceWillReturnTheApiDefinition() = {
      when(apiDefinitionService.fetchApiDefinition(anyString, any[Option[String]])(any[HeaderCarrier]))
        .thenReturn(Future.successful(apiDefinition))
    }

    def theServiceWillFailToReturnTheApiDefinition = {
      when(apiDefinitionService.fetchApiDefinition(anyString, any[Option[String]])(any[HeaderCarrier]))
        .thenReturn(Future.failed(new RuntimeException))
    }

    def theServiceWillReturnTheApiDefinitions() = {
      when(apiDefinitionService.fetchApiDefinitions(any[Option[String]])(any[HeaderCarrier]))
        .thenReturn(Future.successful(apiDefinitions))
    }

    def theServiceWillFailToReturnTheApiDefinitions = {
      when(apiDefinitionService.fetchApiDefinitions(any[Option[String]])(any[HeaderCarrier]))
        .thenReturn(Future.failed(new RuntimeException))
    }
  }

  "fetchApiDefinitions" should {

    "call the service to get the API definitions" in new Setup {
      theServiceWillReturnTheApiDefinitions

      await(underTest.fetchApiDefinitions()(request))

      verify(apiDefinitionService).fetchApiDefinitions(eqTo(None))(any[HeaderCarrier])
    }

    "call the service to get the API definitions for the given user" in new Setup {
      theServiceWillReturnTheApiDefinitions

      await(underTest.fetchApiDefinitions()(requestWithEmailQueryParameter))

      verify(apiDefinitionService).fetchApiDefinitions(eqTo(Some(loggedInUserEmail)))(any[HeaderCarrier])
    }

    "return the API definitions" in new Setup {
      theServiceWillReturnTheApiDefinitions

      val result = await(underTest.fetchApiDefinitions()(request))

      status(result) shouldBe OK
    }

    "fail when the service fails to return the API definitions" in new Setup {
      theServiceWillFailToReturnTheApiDefinitions

      intercept[RuntimeException] {
        await(underTest.fetchApiDefinitions()(request))
      }
    }
  }

  "fetchApiDefinition" should {

    "call the service to get the API definition for a single service" in new Setup {
      theServiceWillReturnTheApiDefinition

      await(underTest.fetchApiDefinition(serviceName)(request))

      verify(apiDefinitionService).fetchApiDefinition(eqTo(serviceName), eqTo(None))(any[HeaderCarrier])
    }

    "call the service to get the API definition for a single service for the given user" in new Setup {
      theServiceWillReturnTheApiDefinition

      await(underTest.fetchApiDefinition(serviceName)(requestWithEmailQueryParameter))

      verify(apiDefinitionService).fetchApiDefinition(eqTo(serviceName), eqTo(Some(loggedInUserEmail)))(any[HeaderCarrier])
    }

    "reutrn the API definition for a single service" in new Setup {
      theServiceWillReturnTheApiDefinition

      val result = await(underTest.fetchApiDefinition(serviceName)(request))

      status(result) shouldBe OK
    }

    "fail when the service fails to return the API definition for a single service" in new Setup {
      theServiceWillFailToReturnTheApiDefinition

      intercept[RuntimeException] {
        await(underTest.fetchApiDefinition(serviceName)(request))
      }
    }
  }
}