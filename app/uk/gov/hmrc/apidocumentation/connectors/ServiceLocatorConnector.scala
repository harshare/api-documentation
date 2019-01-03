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

import javax.inject.Inject

import play.api.Logger
import play.api.http.{ContentTypes, HeaderNames}
import play.api.libs.json.Json
import uk.gov.hmrc.apidocumentation.config.ServiceConfiguration
import uk.gov.hmrc.apidocumentation.models.ServiceDetails
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

case class Registration(serviceName: String, serviceUrl: String, metadata: Option[Map[String, String]] = None)

object Registration {
  implicit val regFormat = Json.format[Registration]
}

class ServiceLocatorConnector @Inject()(http: HttpClient, config: ServiceConfiguration) {

  private lazy val appName: String = config.getString("appName")
  private lazy val appUrl: String = config.getString("appUrl")
  val serviceBaseUrl = config.baseUrl("service-locator")
  val handlerOK: () => Unit = () => Logger.info("Service is registered on the service locator")
  val handlerError: Throwable => Unit = e => Logger.error("Service could not register on the service locator", e)
  val metadata: Option[Map[String, String]] = Some(Map("third-party-api" -> "true"))

  def lookupService(serviceName: String)(implicit hc: HeaderCarrier): Future[ServiceDetails] = {
    Logger.info(s"Calling service locator for: $serviceName")

    http.GET[ServiceDetails](s"$serviceBaseUrl/service/$serviceName")
  }

  def register(): Future[Boolean] = {
    implicit val hc: HeaderCarrier = new HeaderCarrier

    val registration = Registration(appName, appUrl, metadata)
    http.POST(s"$serviceBaseUrl/registration", registration, Seq(HeaderNames.CONTENT_TYPE -> ContentTypes.JSON)) map {
      _ =>
        handlerOK()
        true
    } recover {
      case e: Throwable =>
        handlerError(e)
        false
    }
  }
}
