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

package uk.gov.hmrc.apidocumentation.controllers

import org.mockito.ArgumentMatchers.{any, anyString, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.apidocumentation.models.{ApiAccess, ApiAccessType, ApiAvailability, ApiDefinition, ApiStatus, ExtendedApiDefinition, ExtendedApiVersion}
import uk.gov.hmrc.apidocumentation.services.ApiDefinitionService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class ApiDefinitionControllerSpec extends UnitSpec with ScalaFutures with MockitoSugar with WithFakeApplication {

  trait Setup extends AuthChecking {
    val serviceName = "api-example-microservice"
    val loggedInUserEmail = "john.doe@example.com"

    val apiDefinitions = Seq(
      ApiDefinition(serviceName, "Hello World", "Example", "hello", None, None, Seq.empty),
      ApiDefinition("api-example-person", "Hello Person", "Example", "hello-person", None, None, Seq.empty)
    )
    val apiDefinition = ExtendedApiDefinition(serviceName, "http://hello.protected.mdtp", "Hello World", "Example", "hello",
      requiresTrust = false, isTestSupport = false, Seq(ExtendedApiVersion("1.0", ApiStatus.ALPHA, Seq.empty,
        Some(ApiAvailability(endpointsEnabled = true, ApiAccess(ApiAccessType.PUBLIC, None), loggedIn = false, authorised = false)), None)))

    implicit val mat = fakeApplication.materializer
    val apiDefinitionService = mock[ApiDefinitionService]
    val hc = HeaderCarrier()
    val request = FakeRequest()
    val requestWithEmailQueryParameter = FakeRequest("GET", s"?email=$loggedInUserEmail")

    val underTest = new ApiDefinitionController(apiDefinitionService, mockConfig)

    def theServiceWillReturnTheApiDefinition = {
      when(apiDefinitionService.fetchApiDefinition(anyString, any[Option[String]])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(apiDefinition)))
    }

    def theServiceWillReturnNoApiDefinition = {
      when(apiDefinitionService.fetchApiDefinition(anyString, any[Option[String]])(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
    }

    def theServiceWillFailToReturnTheApiDefinition = {
      when(apiDefinitionService.fetchApiDefinition(anyString, any[Option[String]])(any[HeaderCarrier]))
        .thenReturn(Future.failed(new RuntimeException))
    }

    def theServiceWillReturnTheApiDefinitions = {
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
      authorisationIsNotRequired
      theServiceWillReturnTheApiDefinitions

      await(underTest.fetchApiDefinitions()(request))

      verify(apiDefinitionService).fetchApiDefinitions(eqTo(None))(any[HeaderCarrier])
    }

    "call the service to get the API definitions for the given user" in new Setup {
      authorisationIsNotRequired
      theServiceWillReturnTheApiDefinitions

      await(underTest.fetchApiDefinitions()(requestWithEmailQueryParameter))

      verify(apiDefinitionService).fetchApiDefinitions(eqTo(Some(loggedInUserEmail)))(any[HeaderCarrier])
    }

    "return the API definitions" in new Setup {
      authorisationIsNotRequired
      theServiceWillReturnTheApiDefinitions

      val result = await(underTest.fetchApiDefinitions()(request))

      status(result) shouldBe OK
      Json.parse(bodyOf(result)) shouldBe Json.toJson(apiDefinitions)
    }

    "fail when the service fails to return the API definitions" in new Setup {
      authorisationIsNotRequired
      theServiceWillFailToReturnTheApiDefinitions

      intercept[RuntimeException] {
        await(underTest.fetchApiDefinitions()(request))
      }
    }

    "return Unauthorised when authorisation is required and no Authorization header is present" in new Setup {
      authorisationIsRequired

      val result = await(underTest.fetchApiDefinitions()(request))
      status(result) shouldBe UNAUTHORIZED
    }

    "return Unauthorised when authorisation is required and an Authorization header is present with the wrong value" in new Setup {
      authorisationIsRequired

      val requestWithAuthHeader = request.withHeaders(HeaderNames.AUTHORIZATION -> invalidAuthorizationHeaderValue)
      val result = await(underTest.fetchApiDefinitions()(requestWithAuthHeader))
      status(result) shouldBe UNAUTHORIZED
    }

    "allow the request when authorisation is required and an Authorization header is present with the correct value" in new Setup {
      authorisationIsRequired
      theServiceWillReturnTheApiDefinitions

      val requestWithAuthHeader = request.withHeaders(HeaderNames.AUTHORIZATION -> validAuthorizationHeaderValue)
      val result = await(underTest.fetchApiDefinitions()(requestWithAuthHeader))
      status(result) shouldBe OK
    }
  }

  "fetchApiDefinition" should {

    "call the service to get the API definition for a single service" in new Setup {
      authorisationIsNotRequired
      theServiceWillReturnTheApiDefinition

      await(underTest.fetchApiDefinition(serviceName)(request))

      verify(apiDefinitionService).fetchApiDefinition(eqTo(serviceName), eqTo(None))(any[HeaderCarrier])
    }

    "call the service to get the API definition for a single service for the given user" in new Setup {
      authorisationIsNotRequired
      theServiceWillReturnTheApiDefinition

      await(underTest.fetchApiDefinition(serviceName)(requestWithEmailQueryParameter))

      verify(apiDefinitionService).fetchApiDefinition(eqTo(serviceName), eqTo(Some(loggedInUserEmail)))(any[HeaderCarrier])
    }

    "return the API definition for a single service" in new Setup {
      authorisationIsNotRequired
      theServiceWillReturnTheApiDefinition

      val result = await(underTest.fetchApiDefinition(serviceName)(request))

      status(result) shouldBe OK
    }

    "return NotFound when no API definition is returned for a single service" in new Setup {
      authorisationIsNotRequired
      theServiceWillReturnNoApiDefinition

      val result = await(underTest.fetchApiDefinition(serviceName)(request))

      status(result) shouldBe NOT_FOUND
    }

    "fail when the service fails to return the API definition for a single service" in new Setup {
      authorisationIsNotRequired
      theServiceWillFailToReturnTheApiDefinition

      intercept[RuntimeException] {
        await(underTest.fetchApiDefinition(serviceName)(request))
      }
    }

    "return Unauthorised when authorisation is required and no Authorization header is present" in new Setup {
      authorisationIsRequired

      val result = await(underTest.fetchApiDefinition(serviceName)(request))
      status(result) shouldBe UNAUTHORIZED
    }

    "return Unauthorised when authorisation is required and an Authorization header is present with the wrong value" in new Setup {
      authorisationIsRequired

      val requestWithAuthHeader = request.withHeaders(HeaderNames.AUTHORIZATION -> invalidAuthorizationHeaderValue)
      val result = await(underTest.fetchApiDefinition(serviceName)(requestWithAuthHeader))
      status(result) shouldBe UNAUTHORIZED
    }

    "allow the request when authorisation is required and an Authorization header is present with the correct value" in new Setup {
      authorisationIsRequired
      theServiceWillReturnTheApiDefinition

      val requestWithAuthHeader = request.withHeaders(HeaderNames.AUTHORIZATION -> validAuthorizationHeaderValue)
      val result = await(underTest.fetchApiDefinition(serviceName)(requestWithAuthHeader))
      status(result) shouldBe OK
    }
  }
}
