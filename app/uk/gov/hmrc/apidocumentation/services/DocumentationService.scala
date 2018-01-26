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

package uk.gov.hmrc.apidocumentation.services

import javax.inject.Inject

import play.api.http.HttpEntity
import play.api.http.Status._
import play.api.libs.ws.StreamedResponse
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.apidocumentation.config.ServiceConfiguration
import uk.gov.hmrc.apidocumentation.connectors.{ApiDocumentationConnector, ApiMicroserviceConnector}
import uk.gov.hmrc.apidocumentation.models.ExtendedApiVersion
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException, NotFoundException}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

class DocumentationService @Inject()(apiDefinitionService: ApiDefinitionService,
                                     apiMicroserviceConnector: ApiMicroserviceConnector,
                                     apiDocumentationConnector: ApiDocumentationConnector,
                                     config: ServiceConfiguration) {

  def fetchApiDocumentationResource(serviceName: String, version: String, resource: String)(implicit hc: HeaderCarrier): Future[Result] = {

    def fetchApiVersion: Future[ExtendedApiVersion] = {
      apiDefinitionService.fetchApiDefinition(serviceName).flatMap {
        _.versions.find(_.version == version).fold {
          Future.failed[ExtendedApiVersion](new IllegalArgumentException("Version not found"))
        }(Future.successful)
      }
    }

    def fetchResource(apiVersion: ExtendedApiVersion): Future[StreamedResponse] = {
      if(config.isSandbox) {
        apiMicroserviceConnector.fetchApiDocumentationResource(serviceName, version, resource)
      } else {
        apiVersion.productionAvailability.fold {
          apiDocumentationConnector.fetchApiDocumentationResource(serviceName, version, resource)
        } { _ =>
          apiMicroserviceConnector.fetchApiDocumentationResource(serviceName, version, resource)
        }
      }
    }

    for {
      apiVersion <- fetchApiVersion
      streamedResponse <- fetchResource(apiVersion)
    } yield streamedResponse.headers.status match {
      case OK => {
        val contentType = streamedResponse.headers.headers.get("Content-Type").flatMap(_.headOption)
          .getOrElse("application/octet-stream")

        streamedResponse.headers.headers.get("Content-Length") match {
          case Some(Seq(length)) => Ok.sendEntity(HttpEntity.Streamed(streamedResponse.body, Some(length.toLong), Some(contentType)))
          case _ => Ok.chunked(streamedResponse.body).as(contentType)
        }
      }
      case NOT_FOUND => throw new NotFoundException(s"$resource not found for $serviceName $version")
      case _ => throw new InternalServerException(s"Error downloading $resource for $serviceName $version")
    }
  }
}
