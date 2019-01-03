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

import javax.inject.Inject

import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.apidocumentation.config.ServiceConfiguration
import uk.gov.hmrc.apidocumentation.services.ApiDefinitionService
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

class ApiDefinitionController @Inject() (service: ApiDefinitionService, override val config: ServiceConfiguration) extends BaseController with AuthWrapper {

  def fetchApiDefinitions = ifAuthorised { implicit request =>
    service.fetchApiDefinitions(emailQueryParameter(request)) map {
      definitions => Ok(Json.toJson(definitions))
    }
  }

  def fetchApiDefinition(serviceName: String) = ifAuthorised { implicit request =>
    service.fetchApiDefinition(serviceName, emailQueryParameter(request)) map {
      case Some(definition) => Ok(Json.toJson(definition))
      case _ => NotFound
    }
  }

  private def emailQueryParameter(request: Request[_]) = request.queryString.get("email").flatMap(_.headOption)
}
