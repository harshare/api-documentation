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

package uk.gov.hmrc.apidocumentation.controllers

import play.api.mvc._
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.apidocumentation.config.ServiceConfiguration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.Future

trait AuthWrapper {
  self: Results =>

  val config: ServiceConfiguration

  def ifAuthorised(body: Request[_] => Future[Result]): Action[AnyContent] = {
    Action.async { implicit request =>
      val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

      val allowRequest =
        if (config.requiresAuthorization) hasValidAuthorizationHeader
        else true

      if (allowRequest) body(request)
      else Future.successful(Unauthorized("Not authorised"))
    }
  }

  private def hasValidAuthorizationHeader(implicit request: Request[AnyContent]) = {
    request.headers.get(HeaderNames.AUTHORIZATION).exists(_ == s"Bearer ${config.apiPlatformBearerToken.getOrElse("")}")
  }
}
