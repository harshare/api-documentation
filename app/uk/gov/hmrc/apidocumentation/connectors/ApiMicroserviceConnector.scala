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

import play.api.libs.ws.{StreamedResponse, WSClient}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

class ApiMicroserviceConnector @Inject()(serviceLocator: ServiceLocatorConnector, ws: WSClient) {

  def fetchApiDocumentationResource(serviceName: String, version: String, resource: String)(
    implicit hc: HeaderCarrier): Future[StreamedResponse] = {

    serviceLocator.lookupService(serviceName) flatMap { serviceDetails =>
      ws.url(s"${serviceDetails.serviceUrl}/api/conf/$version/$resource").withMethod("GET").stream()
    }
  }
}