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
import uk.gov.hmrc.apidocumentation.models.{ApiDefinition, ExtendedApiDefinition}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

class ApiDefinitionConnector @Inject()(http: HttpClient, config: ServiceConfiguration) {

  val serviceBaseUrl = config.baseUrl("api-definition")

  def fetchApiDefinition(serviceName: String, email: Option[String] = None)
                        (implicit hc: HeaderCarrier): Future[ExtendedApiDefinition] = {

    http.GET[ExtendedApiDefinition](s"$serviceBaseUrl/api-definition/$serviceName/extended", queryParams(email))
  }

  def fetchApiDefinitions(email: Option[String] = None)
                         (implicit hc: HeaderCarrier): Future[Seq[ApiDefinition]] = {

    http.GET[Seq[ApiDefinition]](s"$serviceBaseUrl/api-definition", queryParams(email)).map(_.sortBy(_.name))
  }

  private def queryParams(email: Option[String]) = email.fold(Seq.empty[(String,String)])(e => Seq("email" -> e))
}

