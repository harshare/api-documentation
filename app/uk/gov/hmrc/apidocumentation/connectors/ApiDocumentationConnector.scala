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

import java.net.URLEncoder
import javax.inject.Inject

import play.api.Logger
import play.api.http.HttpVerbs
import play.api.libs.ws.StreamedResponse
import uk.gov.hmrc.apidocumentation.config.ServiceConfiguration
import uk.gov.hmrc.apidocumentation.models.{ApiDefinition, ExtendedApiDefinition}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

class ApiDocumentationConnector @Inject()(ws: ProxiedApiPlatformWsClient, config: ServiceConfiguration) {


  lazy val enabled = config.getConfBool("api-documentation.enabled", false)
  lazy val serviceBaseUrl = config.baseUrl("api-documentation")

  def fetchApiDefinitions(email: Option[String] = None)
                         (implicit hc: HeaderCarrier): Future[Seq[ApiDefinition]] = {

    if (enabled) {
      ws.buildRequest(s"$serviceBaseUrl/apis/definition")
        .withQueryString(queryParams(email): _*)
        .get()
        .map(response => response.json.as[Seq[ApiDefinition]])
        .map(_.sortBy(_.name))
        .recover {
        case _ => Seq.empty
      }
    } else {
      Future.successful(Seq.empty)
    }
  }

  def fetchApiDefinition(serviceName: String, email: Option[String] = None)
                        (implicit hc: HeaderCarrier): Future[Option[ExtendedApiDefinition]] = {

    if(enabled) {
      ws.buildRequest(s"$serviceBaseUrl/apis/$serviceName/definition")
        .withQueryString(queryParams(email): _*)
        .get()
        .map(response => Some(response.json.as[ExtendedApiDefinition]))
        .recover {
          case _ => None
        }
    } else {
      Future.successful(None)
    }
  }

  def fetchApiDocumentationResource(serviceName: String, version: String, resource: String)
                                   (implicit hc: HeaderCarrier): Future[StreamedResponse] = {
    Logger.info(s"Calling remote API documentation service to fetch documentation resource: $serviceName, $version, $resource")

    ws.buildRequest(s"$serviceBaseUrl/apis/$serviceName/$version/documentation?resource=${URLEncoder.encode(resource, "UTF-8")}")
      .withMethod(HttpVerbs.GET)
      .stream()
  }

  private def queryParams(email: Option[String]) = email.fold(Seq.empty[(String, String)])(e => Seq("email" -> e))

}
