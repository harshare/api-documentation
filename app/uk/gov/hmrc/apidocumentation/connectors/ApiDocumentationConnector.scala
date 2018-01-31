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

import javax.inject.Inject

import play.api.Logger
import play.api.http.Status.NOT_FOUND
import play.api.libs.ws.{StreamedResponse, WSClient}
import uk.gov.hmrc.apidocumentation.config.ServiceConfiguration
import uk.gov.hmrc.apidocumentation.models.{ApiDefinition, ExtendedApiDefinition}
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.apidocumentation.models.ApiDefinition
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

class ApiDocumentationConnector @Inject()(http: ProxiedHttpClient, ws: WSClient, config: ServiceConfiguration) {


  val serviceBaseUrl = config.baseUrl("api-documentation")

  val enabled = config.getConfBool("api-documentation.enabled", false)

  def fetchApiDefinitions(email: Option[String] = None)
                         (implicit hc: HeaderCarrier): Future[Seq[ApiDefinition]] = {

    if (enabled) {
      http.GET[Seq[ApiDefinition]](s"$serviceBaseUrl/apis/definition", queryParams(email)).map(_.sortBy(_.name)) recover {
        case _ => Seq.empty
      }
    } else {
      Future.successful(Seq.empty)
    }
  }

  def fetchApiDefinition(serviceName: String, email: Option[String] = None)
                        (implicit hc: HeaderCarrier): Future[Option[ExtendedApiDefinition]] = {

    if(enabled) {
      http.GET[ExtendedApiDefinition](s"$serviceBaseUrl/apis/$serviceName/definition", queryParams(email))
        .map(Some(_))
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

    ws.url(s"$serviceBaseUrl/apis/$serviceName/$version/documentation/$resource").withMethod("GET").stream()
  }

  private def queryParams(email: Option[String]) = email.fold(Seq.empty[(String, String)])(e => Seq("email" -> e))

}
