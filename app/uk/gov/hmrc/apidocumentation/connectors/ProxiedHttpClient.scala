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
