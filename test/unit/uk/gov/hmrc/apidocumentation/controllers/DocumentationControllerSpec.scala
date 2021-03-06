/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status._
import play.api.mvc.Results
import play.api.test.FakeRequest
import uk.gov.hmrc.apidocumentation.config.ServiceConfiguration
import uk.gov.hmrc.apidocumentation.services.DocumentationService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class DocumentationControllerSpec  extends UnitSpec with ScalaFutures with MockitoSugar with WithFakeApplication {

  trait Setup extends AuthChecking {
    implicit val mat = fakeApplication.materializer
    val request = FakeRequest()
    val documentationService = mock[DocumentationService]
    val hc = HeaderCarrier()
    val serviceName = "api-example-microservice"
    val version = "1.0"
    val resourceName = "application.raml"
    val body = "RAML doc content"
    val contentType = "application/text"

    val underTest = new DocumentationController(documentationService, mockConfig)

    def theServiceWillReturnTheResource = {
      when(documentationService.fetchApiDocumentationResource(anyString, anyString, anyString)(any[HeaderCarrier]))
        .thenReturn(Future.successful(Results.Ok(body).withHeaders(CONTENT_TYPE -> contentType)))
    }

    def theServiceWillFailToReturnTheResource = {
      when(documentationService.fetchApiDocumentationResource(anyString, anyString, anyString)(any[HeaderCarrier]))
        .thenReturn(Future.failed(new RuntimeException))
    }
  }

  "DocumentationController" should {

    "call the service to get the resource" in new Setup {
      authorisationIsNotRequired
      theServiceWillReturnTheResource

      await(underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request))

      verify(documentationService).fetchApiDocumentationResource(eqTo(serviceName), eqTo(version), eqTo(resourceName))(any[HeaderCarrier])
    }

    "return the resource with a Content-type header when the content type is known" in new Setup {
      authorisationIsNotRequired
      theServiceWillReturnTheResource

      val result = await(underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request))

      status(result) shouldBe OK
      bodyOf(result) shouldBe body
      result.header.headers(CONTENT_TYPE) shouldBe contentType
    }

    "return the resource with no Content-type header when the content type is unknown" in new Setup {
      authorisationIsNotRequired
      theServiceWillReturnTheResource

      val result = await(underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request))

      status(result) shouldBe OK
      bodyOf(result) shouldBe body
    }

    "fail when the service fails to return the resource" in new Setup {
      authorisationIsNotRequired
      theServiceWillFailToReturnTheResource

      intercept[RuntimeException] {
        await(underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request))
      }
    }

    "return Unauthorised when authorisation is required and no Authorization header is present" in new Setup {
      authorisationIsRequired

      val result = await(underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request))
      status(result) shouldBe UNAUTHORIZED
    }

    "return Unauthorised when authorisation is required and an Authorization header is present with the wrong value" in new Setup {
      authorisationIsRequired

      val requestWithAuthHeader = request.withHeaders(HeaderNames.AUTHORIZATION -> invalidAuthorizationHeaderValue)
      val result = await(underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(requestWithAuthHeader))
      status(result) shouldBe UNAUTHORIZED
    }

    "allow the request when authorisation is required and an Authorization header is present with the correct value" in new Setup {
      authorisationIsRequired
      theServiceWillReturnTheResource

      val requestWithAuthHeader = request.withHeaders(HeaderNames.AUTHORIZATION -> validAuthorizationHeaderValue)
      val result = await(underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(requestWithAuthHeader))
      status(result) shouldBe OK
    }
  }
}
