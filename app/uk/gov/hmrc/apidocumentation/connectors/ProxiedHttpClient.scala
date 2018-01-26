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

import com.typesafe.config.Config
import play.api.Configuration
import play.api.libs.ws.{WSClient, WSProxyServer}
import uk.gov.hmrc.apidocumentation.config.ServiceConfiguration
import uk.gov.hmrc.apidocumentation.models.ApiDefinition
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.AppName
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.http.ws.{WSHttp, WSProxy, WSProxyConfiguration}

class ProxiedHttpClient @Inject()(config: ServiceConfiguration, override val auditConnector: AuditConnector, override val wsClient: WSClient) extends HttpClient with WSHttp with HttpAuditing with WSProxy {
  override def wsProxyServer: Option[WSProxyServer] = WSProxyConfiguration(s"${config.env}.proxy")

  lazy val authorization = Authorization(s"Bearer ${config.getString("api.documentation.bearer")}")

  override def buildRequest[A](url: String)(implicit hc: HeaderCarrier) = {
    val hcWithBearerAndAccept = hc.copy(authorization = Some(authorization),
      extraHeaders = hc.extraHeaders :+ ("Accept" -> "application/hmrc.vnd.1.0+json"))

    super.buildRequest(url)(hcWithBearerAndAccept)
  }

  override lazy val configuration: Option[Config] = Option(config.runModeConfiguration.underlying)

  override lazy val appName: String = new AppName {
    override def configuration: Configuration = config.runModeConfiguration
  }.appName

  override val hooks = Seq(AuditingHook)
}
