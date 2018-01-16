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

package uk.gov.hmrc.apidocumentation.connectors

import java.net.URLEncoder

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Configuration, Environment}
import uk.gov.hmrc.apidocumentation.config.ServiceConfiguration
import uk.gov.hmrc.apidocumentation.utils.TestHttpClient
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

class ApiDocumentationConnectorSpec extends UnitSpec with ScalaFutures with BeforeAndAfterEach with GuiceOneAppPerSuite with MockitoSugar {


  val apiDocumentationPort = sys.env.getOrElse("WIREMOCK", "11114").toInt
  var apiDocumentationHost = "localhost"
  val apiDocumentationUrl = s"http://$apiDocumentationHost:$apiDocumentationPort"
  val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiDocumentationPort))
  val loggedInUserEmail = "john.doe@example.com"
  val encodedLoggedInUserMail = URLEncoder.encode(loggedInUserEmail, "UTF-8")

  class TestServiceConfiguration(bool: Boolean = true) extends ServiceConfiguration(mock[Configuration], mock[Environment]) {
    override def baseUrl(serviceName: String): String = apiDocumentationUrl

    override def getConfBool(confKey: String, defBool: => Boolean): Boolean = bool
  }

  trait Setup {
    implicit val hc = HeaderCarrier()
    val connector = new ApiDocumentationConnector(new TestHttpClient(), new TestServiceConfiguration)
  }

  override def beforeEach() {
    wireMockServer.start()
    WireMock.configureFor(apiDocumentationHost, apiDocumentationPort)
  }

  override def afterEach() {
    wireMockServer.stop()
  }

  "fetchApiDefinitions" should {

    "return all API Definitions sorted by name" in new Setup {
      stubFor(get(urlEqualTo(s"/apis/definition"))
        .willReturn(aResponse().withStatus(200).withBody(apiDefinitionsJson("Hello", "Calendar"))))

      val result = await(connector.fetchApiDefinitions())
      result.size shouldBe 2
      result.head.name shouldBe "Calendar"
      result(1).name shouldBe "Hello"
    }

    "return all API Definitions sorted by name for an email address" in new Setup {
      stubFor(get(urlEqualTo(s"/apis/definition?email=$encodedLoggedInUserMail"))
        .willReturn(aResponse().withStatus(200).withBody(apiDefinitionsJson("Hello", "Calendar"))))

      val result = await(connector.fetchApiDefinitions(Some(loggedInUserEmail)))
      result.size shouldBe 2
      result.head.name shouldBe "Calendar"
      result(1).name shouldBe "Hello"
    }

    "return all API Definitions sorted by name for a strange email address" in new Setup {
      val loggedInUserStrangeEmail = "email+strange@email.com"
      val encodedLoggedInUserStrangeEmail = URLEncoder.encode(loggedInUserStrangeEmail, "UTF-8")
      stubFor(get(urlEqualTo(s"/apis/definition?email=$encodedLoggedInUserStrangeEmail"))
        .willReturn(aResponse().withStatus(200).withBody(apiDefinitionsJson("Hello", "Calendar"))))

      val result = await(connector.fetchApiDefinitions(Some(loggedInUserStrangeEmail)))
      result.size shouldBe 2
      result.head.name shouldBe "Calendar"
      result(1).name shouldBe "Hello"
    }

    "return an empty Sequence if the API Documentation service responds with an error" in new Setup {
      stubFor(get(urlEqualTo(s"/apis/definition?email=$encodedLoggedInUserMail"))
        .willReturn(aResponse().withStatus(500)))

      val result = await(connector.fetchApiDefinitions(Some(loggedInUserEmail)))
      result.size shouldBe 0
    }
    
    "return an empty Sequence if the remote call is disabled" in new Setup {
     override val connector = new ApiDocumentationConnector(new TestHttpClient(), new TestServiceConfiguration(false))

      val result = await(connector.fetchApiDefinitions(Some(loggedInUserEmail)))

      result.size shouldBe 0
    }
  }

  private def apiDefinitionsJson(names: String*) = {
    names.map(apiDefinitionJson).mkString("[", ",", "]")
  }

  private def apiDefinitionJson(name: String) = {
    s"""{
       |  "name" : "$name",
       |  "description" : "Test API",
       |  "context" : "test",
       |  "serviceBaseUrl" : "http://test",
       |  "serviceName" : "test",
       |  "versions" : [
       |    {
       |      "version" : "1.0",
       |      "status" : "STABLE",
       |      "endpoints" : [
       |        {
       |          "uriPattern" : "/hello",
       |          "endpointName" : "Say Hello",
       |          "method" : "GET",
       |          "authType" : "NONE",
       |          "throttlingTier" : "UNLIMITED"
       |        }
       |      ]
       |    },
       |    {
       |      "version" : "2.0",
       |      "status" : "STABLE",
       |      "endpoints" : [
       |        {
       |          "uriPattern" : "/hello",
       |          "endpointName" : "Say Hello",
       |          "method" : "GET",
       |          "authType" : "NONE",
       |          "throttlingTier" : "UNLIMITED",
       |          "scope": "read:hello"
       |        }
       |      ]
       |    }
       |  ]
       |}""".stripMargin.replaceAll("\n", " ")
  }

}
