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

package uk.gov.hmrc.apidocumentation.connectors

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.apidocumentation.config.ServiceConfiguration
import uk.gov.hmrc.apidocumentation.utils.TestHttpClient
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

class ServiceLocatorConnectorSpec extends UnitSpec with ScalaFutures with BeforeAndAfterEach with GuiceOneAppPerSuite with MockitoSugar {

  val serviceLocatorPort = sys.env.getOrElse("WIREMOCK", "11112").toInt
  var serviceLocatorHost = "localhost"
  val serviceLocatorUrl = s"http://$serviceLocatorHost:$serviceLocatorPort"
  val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(serviceLocatorPort))

  trait Setup {
    val serviceConfiguration = mock[ServiceConfiguration]
    when(serviceConfiguration.baseUrl("service-locator")).thenReturn(serviceLocatorUrl)

    implicit val hc = HeaderCarrier()
    val connector = new ServiceLocatorConnector(new TestHttpClient(), serviceConfiguration)
  }

  override def beforeEach() {
    wireMockServer.start()
    WireMock.configureFor(serviceLocatorHost, serviceLocatorPort)
  }

  override def afterEach() {
    wireMockServer.stop()
  }

  "lookupService" should {

    "return details for the service" in new Setup {
      val serviceName = "calendar"
      val serviceUrl = "http://localhost:9000/calendar"
      stubFor(get(urlEqualTo(s"/service/$serviceName"))
        .willReturn(aResponse().withStatus(200)
        .withBody( s"""{"serviceName":"$serviceName","serviceUrl":"$serviceUrl"}""")))

      val result = await(connector.lookupService(serviceName))

      result.serviceName shouldBe serviceName
      result.serviceUrl shouldBe serviceUrl
    }

    "throw an http-verbs Upstream5xxResponse exception if the Service Locator responds with an error" in new Setup {
      val serviceName = "calendar"
      stubFor(get(urlEqualTo(s"/service/$serviceName")).willReturn(aResponse().withStatus(500)))
      intercept[Upstream5xxResponse](await(connector.lookupService(serviceName)))
    }

    "throw a DocumentationNotFound exception if service is not available in Service Locator" in new Setup {
      val serviceName = "unknown"
      stubFor(get(urlEqualTo(s"/service/$serviceName")).willReturn(aResponse().withStatus(404)))
      intercept[NotFoundException](await(connector.lookupService(serviceName)))
    }
  }
}
