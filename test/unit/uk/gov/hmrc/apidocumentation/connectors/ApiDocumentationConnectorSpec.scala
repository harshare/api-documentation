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

import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import mockws.MockWS
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HttpEntity
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, ResponseHeader, Result}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.apidocumentation.config.ServiceConfiguration
import uk.gov.hmrc.apidocumentation.models.{ApiAccess, ApiAccessType}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

class ApiDocumentationConnectorSpec extends UnitSpec with ScalaFutures with BeforeAndAfterEach with GuiceOneAppPerSuite with MockitoSugar {


  val apiDocumentationPort = sys.env.getOrElse("WIREMOCK", "11114").toInt
  var apiDocumentationHost = "localhost"
  val apiDocumentationUrl = s"http://$apiDocumentationHost:$apiDocumentationPort"
  val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiDocumentationPort))
  val loggedInUserEmail = "john.doe@example.com"
  val encodedLoggedInUserMail = URLEncoder.encode(loggedInUserEmail, "UTF-8")
  val serviceName = "hello"
  val version = "1.0"
  val streamedResourceUrl = s"$apiDocumentationUrl/apis/$serviceName/$version/resource"
  val file = new java.io.File("hello")
  val path: java.nio.file.Path = file.toPath
  val source: Source[ByteString, _] = FileIO.fromPath(path)

  val mockWs = MockWS {
    case ("GET", `streamedResourceUrl`) => Action(Result(
      header = ResponseHeader(200, Map("Content-length" -> s"${file.length()}")),
      body = HttpEntity.Streamed(source, Some(file.length()), Some("application/pdf"))
    ))
  }


  class TestServiceConfiguration(bool: Boolean = true) extends ServiceConfiguration(app.injector.instanceOf[Configuration], app.injector.instanceOf[Environment]) {
    override def baseUrl(serviceName: String): String = apiDocumentationUrl

    override def getString(key: String): String = "BEARER"

    override def getConfBool(confKey: String, defBool: => Boolean): Boolean = bool
  }

  trait Setup {
    implicit val hc = HeaderCarrier()
    private val testServiceConfiguration = new TestServiceConfiguration
    val connector = new ApiDocumentationConnector(
      new ProxiedApiPlatformWsClient(testServiceConfiguration, app.injector.instanceOf[WSClient]),
      testServiceConfiguration)
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
      WireMock.resetAllRequests()
      val configuration = new TestServiceConfiguration(false)
      val wsClient = new ProxiedApiPlatformWsClient(configuration, app.injector.instanceOf[WSClient])
      override val connector = new ApiDocumentationConnector(wsClient, configuration)

      val result = await(connector.fetchApiDefinitions(Some(loggedInUserEmail)))
      result.size shouldBe 0
      verify(0, getRequestedFor(urlEqualTo(s"/apis/definition?email=$encodedLoggedInUserMail")))
    }
  }

  "fetchApiDefinition" should {

    "return a fetched API Definition" in new Setup {
      val serviceName = "calendar"
      stubFor(get(urlEqualTo(s"/apis/$serviceName/definition"))
        .willReturn(aResponse().withStatus(200).withBody(extendedApiDefinitionJson("Calendar"))))

      val result = await(connector.fetchApiDefinition(serviceName))
      result.get.name shouldBe "Calendar"
      result.get.versions should have size 2
      result.get.versions map (_.productionAvailability.map(_.access)) shouldBe
        Seq(Some(ApiAccess(ApiAccessType.PUBLIC, None)), Some(ApiAccess(ApiAccessType.PRIVATE, Some(Seq("app-id-1", "app-id-2")))))
    }

    "return a fetched API Definition when queried by email" in new Setup {
      val serviceName = "calendar"
      stubFor(get(urlEqualTo(s"/apis/$serviceName/definition?email=$encodedLoggedInUserMail"))
        .willReturn(aResponse().withStatus(200).withBody(extendedApiDefinitionJson("Calendar"))))

      val result = await(connector.fetchApiDefinition(serviceName, Some(loggedInUserEmail)))
      result.get.name shouldBe "Calendar"
      result.get.versions should have size 2
      result.get.versions map (_.productionAvailability.map(_.access)) shouldBe
        Seq(Some(ApiAccess(ApiAccessType.PUBLIC, None)), Some(ApiAccess(ApiAccessType.PRIVATE, Some(Seq("app-id-1", "app-id-2")))))
    }

    "return None if the remote service responds with an error" in new Setup {
      val serviceName = "calendar"
      stubFor(get(urlEqualTo(s"/apis/$serviceName/definition"))
        .willReturn(aResponse().withStatus(500)))

      val result = await(connector.fetchApiDefinition(serviceName))
      result shouldBe None
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
}
