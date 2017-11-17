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

package uk.gov.hmrc.apidocumentation.connectors

import javax.inject.Inject

import uk.gov.hmrc.apidocumentation.config.ServiceConfiguration
import uk.gov.hmrc.apidocumentation.models.ServiceDetails
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.http.metrics.{API, Metrics}

import scala.concurrent.Future

class ServiceLocatorConnector @Inject()(http: HttpClient, metrics: Metrics, config: ServiceConfiguration) {

  val api = API("service-locator")
  val serviceBaseUrl = config.baseUrl("service-locator")

  def lookupService(serviceName: String)(implicit hc: HeaderCarrier): Future[ServiceDetails] = metrics.record(api) {
    http.GET[ServiceDetails](s"$serviceBaseUrl/service/$serviceName")
  }
}

