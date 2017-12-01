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

import javax.inject.Inject

import play.api.http.HttpEntity
import play.api.http.Status._
import play.api.libs.ws.{StreamedResponse, WSClient}
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.apidocumentation.connectors.ServiceLocatorConnector
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException, NotFoundException}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

class DocumentationService @Inject()(serviceLocator: ServiceLocatorConnector, ws: WSClient) {

  def fetchApiDocumentationResource(serviceName: String, version: String, resource: String)(implicit hc: HeaderCarrier): Future[Result] = {
    serviceLocator.lookupService(serviceName) flatMap { serviceDetails =>
      ws.url(s"${serviceDetails.serviceUrl}/api/conf/$version/$resource").withMethod("GET").stream()
    } map { case StreamedResponse(response, body) =>
      response.status match {
        case OK => {
          val contentType = response.headers.get("Content-Type").flatMap(_.headOption)
            .getOrElse("application/octet-stream")

          response.headers.get("Content-Length") match {
            case Some(Seq(length)) => Ok.sendEntity(HttpEntity.Streamed(body, Some(length.toLong), Some(contentType)))
            case _ => Ok.chunked(body).as(contentType)
          }
        }
        case NOT_FOUND => throw new NotFoundException(s"$resource not found for $serviceName $version")
        case _ => throw new InternalServerException(s"Error downloading $resource for $serviceName $version")
      }
    }
  }
}
