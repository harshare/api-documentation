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

import play.api.http.HeaderNames.CONTENT_TYPE
import uk.gov.hmrc.apidocumentation.connectors.ServiceLocatorConnector
import uk.gov.hmrc.apidocumentation.models.DocumentationResource
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

class DocumentationService @Inject()(serviceLocator: ServiceLocatorConnector, http: HttpClient) {

  def fetchApiDocumentationResource(serviceName: String, version: String, resource: String)(implicit hc: HeaderCarrier): Future[DocumentationResource] = {
    serviceLocator.lookupService(serviceName) flatMap { serviceDetails =>
      http.GET[HttpResponse](s"${serviceDetails.serviceUrl}/api/conf/$version/$resource")
    } map { httpResponse =>
      DocumentationResource(httpResponse.body, httpResponse.header(CONTENT_TYPE))
    }
  }
}