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

package uk.gov.hmrc.apidocumentation.connectors

import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.http.HeaderNames
import play.api.libs.ws.WSClient
import uk.gov.hmrc.apidocumentation.config.ServiceConfiguration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

class ProxiedApiPlatformWsClientSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite {

  trait Setup {
    implicit val hc = HeaderCarrier()
    val mockConfig = mock[ServiceConfiguration]
    val wsClient = app.injector.instanceOf[WSClient]

    def aBearerTokenWillBeConfigured = {
      when(mockConfig.apiPlatformBearerToken).thenReturn(Some("BEARERTOKEN"))
    }

    def noBearerTokenWillBeConfigured = {
      when(mockConfig.apiPlatformBearerToken).thenReturn(None)
    }

    def aProxyWillBeConfigured = {
      when(mockConfig.env).thenReturn("Test")
      when(mockConfig.runModeConfiguration).thenReturn(Configuration(
        "Test.proxy.proxyRequiredForThisEnvironment" -> true,
        "Test.proxy.protocol" -> "https",
        "Test.proxy.host" -> "proxyhost",
        "Test.proxy.port" -> "443",
        "Test.proxy.username" -> "proxyuser",
        "Test.proxy.password" -> "proxypassword"
      ))
    }

    def noProxyWillBeConfigured = {
      when(mockConfig.env).thenReturn("Test")
      when(mockConfig.runModeConfiguration).thenReturn(Configuration("Test.proxy.proxyRequiredForThisEnvironment" -> false))
    }
  }

  "ProxiedApiPlatformWsClient" should {

    "add the bearer token to the request when configured to do so" in new Setup {
      aBearerTokenWillBeConfigured
      aProxyWillBeConfigured

      val ws = new ProxiedApiPlatformWsClient(mockConfig, wsClient)
      val request = ws.buildRequest("/u/r/l")
      request.headers.get(HeaderNames.AUTHORIZATION) shouldBe Some(Seq("Bearer BEARERTOKEN"))
    }

    "not add the bearer token to the request when one is not configured" in new Setup {
      noBearerTokenWillBeConfigured
      noProxyWillBeConfigured

      val ws = new ProxiedApiPlatformWsClient(mockConfig, wsClient)
      val request = ws.buildRequest("/u/r/l")
      request.headers.get(HeaderNames.AUTHORIZATION) shouldBe None
    }

    "include the proxy when configured to do so" in new Setup {
      aBearerTokenWillBeConfigured
      aProxyWillBeConfigured

      val ws = new ProxiedApiPlatformWsClient(mockConfig, wsClient)
      val request = ws.buildRequest("/u/r/l")
      request.proxyServer shouldBe defined
    }

    "not include the proxy when one is not configured" in new Setup {
      aBearerTokenWillBeConfigured
      noProxyWillBeConfigured

      val ws = new ProxiedApiPlatformWsClient(mockConfig, wsClient)
      val request = ws.buildRequest("/u/r/l")
      request.proxyServer shouldBe empty
    }
  }
}
