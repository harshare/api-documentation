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

import org.mockito.ArgumentMatchers.{any, anyString, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.HeaderNames.CONTENT_TYPE
import uk.gov.hmrc.apidocumentation.connectors.ServiceLocatorConnector
import uk.gov.hmrc.apidocumentation.models.ServiceDetails
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, Upstream4xxResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class DocumentationServiceSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  trait Setup {
    val httpClient = mock[HttpClient]
    val serviceLocatorConnector = mock[ServiceLocatorConnector]
    val response = mock[HttpResponse]
    val body = "RAML doc content"
    val contentType = "application/text"
    val hc = new HeaderCarrier()

    val serviceName = "api-example-microservice"
    val serviceUrl = "http://api-example-microservice.protected.mdtp"

    val underTest = new DocumentationService(serviceLocatorConnector, httpClient)

    def serviceLocatorWillReturnTheServiceDetails = {
      when(serviceLocatorConnector.lookupService(anyString)(any[HeaderCarrier]))
        .thenReturn(Future.successful(ServiceDetails(serviceName, serviceUrl)))
    }

    def serviceLocatorWillFailToReturnTheServiceDetails = {
      when(serviceLocatorConnector.lookupService(anyString)(any[HeaderCarrier]))
        .thenReturn(Future.failed(new Upstream4xxResponse("Not found", 404, 404)))
    }

    def theServiceWillReturnTheResource = {
      when(httpClient.GET[HttpResponse](any)(any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(response))
      when(response.body).thenReturn(body)
      when(response.header(CONTENT_TYPE)).thenReturn(Some(contentType))
    }

    def theServiceWillFailToReturnTheResource = {
      when(httpClient.GET[HttpResponse](any)(any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new Upstream4xxResponse("Not found", 404, 404)))
    }
  }

  "DocumentationService" should {

    "ask service locator for the service URL" in new Setup {
      serviceLocatorWillReturnTheServiceDetails
      theServiceWillReturnTheResource

      val result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "application.raml")(hc))

      verify(serviceLocatorConnector).lookupService(eqTo(serviceName))(any[HeaderCarrier])
    }

    "call the service to fetch the resource" in new Setup {
      serviceLocatorWillReturnTheServiceDetails
      theServiceWillReturnTheResource

      val result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "application.raml")(hc))

      verify(httpClient).GET(eqTo(s"$serviceUrl/api/conf/1.0/application.raml"))(any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])
    }

    "return the resource" in new Setup {
      serviceLocatorWillReturnTheServiceDetails
      theServiceWillReturnTheResource

      val result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "application.raml")(hc))

      result.body shouldBe body
      result.contentType shouldBe Some(contentType)
    }

    "fail when service locator does not find the service URL" in new Setup {
      serviceLocatorWillFailToReturnTheServiceDetails

      intercept[Upstream4xxResponse] {
        await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "application.raml")(hc))
      }
    }

    "fail when the service does not return the resource" in new Setup {
      serviceLocatorWillReturnTheServiceDetails
      theServiceWillFailToReturnTheResource

      intercept[Upstream4xxResponse] {
        await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "application.raml")(hc))
      }
    }
  }
}
