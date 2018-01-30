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
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.apidocumentation.config.ServiceConfiguration
import uk.gov.hmrc.apidocumentation.models.{ApiAccess, ApiAccessType}
import uk.gov.hmrc.apidocumentation.utils.TestHttpClient
import uk.gov.hmrc.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

class ApiDefinitionConnectorSpec extends UnitSpec with ScalaFutures with BeforeAndAfterEach with GuiceOneAppPerSuite with MockitoSugar {

  val apiDefinitionPort = sys.env.getOrElse("WIREMOCK", "11114").toInt
  var apiDefinitionHost = "localhost"
  val apiDefinitionUrl = s"http://$apiDefinitionHost:$apiDefinitionPort"
  val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiDefinitionPort))
  val loggedInUserEmail = "john.doe@example.com"
  val encodedLoggedInUserMail = URLEncoder.encode(loggedInUserEmail, "UTF-8")


  trait Setup {
    val serviceConfiguration = mock[ServiceConfiguration]
    when(serviceConfiguration.baseUrl("api-definition")).thenReturn(apiDefinitionUrl)

    implicit val hc = HeaderCarrier()
    val connector = new ApiDefinitionConnector(new TestHttpClient(), serviceConfiguration)
  }

  override def beforeEach() {
    wireMockServer.start()
    WireMock.configureFor(apiDefinitionHost, apiDefinitionPort)
  }

  override def afterEach() {
    wireMockServer.stop()
  }

  "fetchApiDefinition" should {

    "return a fetched API Definition" in new Setup {
      val serviceName = "calendar"
      stubFor(get(urlEqualTo(s"/api-definition/$serviceName/extended"))
        .willReturn(aResponse().withStatus(200).withBody(extendedApiDefinitionJson("Calendar"))))

      val result = await(connector.fetchApiDefinition(serviceName))
      result.get.name shouldBe "Calendar"
      result.get.versions should have size 2
      result.get.versions map (_.productionAvailability.map(_.access)) shouldBe
        Seq(Some(ApiAccess(ApiAccessType.PUBLIC, None)), Some(ApiAccess(ApiAccessType.PRIVATE, Some(Seq("app-id-1","app-id-2")))))
    }

    "return a fetched API Definition when queried by email" in new Setup {
      val serviceName = "calendar"
      stubFor(get(urlEqualTo(s"/api-definition/$serviceName/extended?email=$encodedLoggedInUserMail"))
        .willReturn(aResponse().withStatus(200).withBody(extendedApiDefinitionJson("Calendar"))))

      val result = await(connector.fetchApiDefinition(serviceName, Some(loggedInUserEmail)))
      result.get.name shouldBe "Calendar"
      result.get.versions should have size 2
      result.get.versions map (_.productionAvailability.map(_.access)) shouldBe
        Seq(Some(ApiAccess(ApiAccessType.PUBLIC, None)), Some(ApiAccess(ApiAccessType.PRIVATE, Some(Seq("app-id-1","app-id-2")))))
    }

    "return a fetched API Definition with access levels" in new Setup {
      val serviceName = "calendar"
      stubFor(get(urlEqualTo(s"/api-definition/$serviceName/extended"))
        .willReturn(aResponse().withStatus(200).withBody(extendedApiDefinitionJson("Hello with access levels"))))

      val result = await(connector.fetchApiDefinition(serviceName))
      result shouldBe defined
      result.get.name shouldBe "Hello with access levels"
      result.get.versions should have size 2
      result.get.versions map (_.productionAvailability.map(_.access)) shouldBe
        Seq(Some(ApiAccess(ApiAccessType.PUBLIC, None)), Some(ApiAccess(ApiAccessType.PRIVATE, Some(Seq("app-id-1","app-id-2")))))
    }

    "throw an http-verbs Upstream5xxResponse exception if the API Definition service responds with an error" in new Setup {
      val serviceName = "calendar"
      stubFor(get(urlEqualTo(s"/api-definition/$serviceName/extended"))
        .willReturn(aResponse().withStatus(500)))

      intercept[Upstream5xxResponse] {
        await(connector.fetchApiDefinition(serviceName))
      }
    }
  }

  "fetchApiDefinitions" should {

    "return all API Definitions sorted by name" in new Setup {
      stubFor(get(urlEqualTo(s"/api-definition"))
        .willReturn(aResponse().withStatus(200).withBody(apiDefinitionsJson("Hello", "Calendar"))))

      val result = await(connector.fetchApiDefinitions())
      result.size shouldBe 2
      result(0).name shouldBe "Calendar"
      result(1).name shouldBe "Hello"
    }

    "return all API Definitions sorted by name for an email address" in new Setup {
      stubFor(get(urlEqualTo(s"/api-definition?email=$encodedLoggedInUserMail"))
        .willReturn(aResponse().withStatus(200).withBody(apiDefinitionsJson("Hello", "Calendar"))))

      val result = await(connector.fetchApiDefinitions(Some(loggedInUserEmail)))
      result.size shouldBe 2
      result(0).name shouldBe "Calendar"
      result(1).name shouldBe "Hello"
    }

    "return all API Definitions sorted by name for a strange email address" in new Setup {
      val loggedInUserStrangeEmail = "email+strange@email.com"
      val encodedLoggedInUserStrangeEmail = URLEncoder.encode(loggedInUserStrangeEmail, "UTF-8")
      stubFor(get(urlEqualTo(s"/api-definition?email=$encodedLoggedInUserStrangeEmail"))
        .willReturn(aResponse().withStatus(200).withBody(apiDefinitionsJson("Hello", "Calendar"))))

      val result = await(connector.fetchApiDefinitions(Some(loggedInUserStrangeEmail)))
      result.size shouldBe 2
      result(0).name shouldBe "Calendar"
      result(1).name shouldBe "Hello"
    }

    "throw an http-verbs Upstream5xxResponse exception if the API Definition service responds with an error" in new Setup {
      stubFor(get(urlEqualTo(s"/api-definition?email=$encodedLoggedInUserMail"))
        .willReturn(aResponse().withStatus(500)))

      intercept[Upstream5xxResponse] {
        await(connector.fetchApiDefinitions(Some(loggedInUserEmail)))
      }
    }
  }

  private def apiDefinitionsJson(names: String*) = {
    names.map(apiDefinitionJson).mkString("[", ",", "]")
  }

  private def extendedApiDefinitionJson(name: String) = {
    s"""{
       |  "name" : "$name",
       |  "description" : "Test API",
       |  "context" : "test",
       |  "serviceBaseUrl" : "http://test",
       |  "serviceName" : "test",
       |  "requiresTrust": false,
       |  "isTestSupport": false,
       |  "versions" : [
       |    {
       |      "version" : "1.0",
       |      "status" : "STABLE",
       |      "endpoints" : [
       |        {
       |          "uriPattern" : "/hello",
       |          "endpointName" : "Say Hello Publicly",
       |          "method" : "GET",
       |          "authType" : "NONE",
       |          "throttlingTier" : "UNLIMITED"
       |        }
       |      ],
       |      "productionAvailability": {
       |        "endpointsEnabled": true,
       |        "access": {
       |          "type": "PUBLIC"
       |        },
       |        "loggedIn": false,
       |        "authorised": true
       |      }
       |    },
       |    {
       |      "version" : "2.0",
       |      "status" : "STABLE",
       |      "endpoints" : [
       |        {
       |          "uriPattern" : "/hello",
       |          "endpointName" : "Say Hello Privately",
       |          "method" : "GET",
       |          "authType" : "NONE",
       |          "throttlingTier" : "UNLIMITED",
       |          "scope": "read:hello"
       |        }
       |      ],
       |      "productionAvailability": {
       |        "endpointsEnabled": true,
       |        "access" : {
       |          "type" : "PRIVATE",
       |          "whitelistedApplicationIds" : ["app-id-1","app-id-2"]
       |        },
       |        "loggedIn": false,
       |        "authorised": true
       |      }
       |    }
       |  ]
       |}
     """.stripMargin
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
       |      ],
       |      "endpointsEnabled": true
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
       |      ],
       |      "endpointsEnabled": true
       |    }
       |  ]
       |}""".stripMargin.replaceAll("\n", " ")
  }
}
