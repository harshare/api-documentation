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

package uk.gov.hmrc.apidocumentation.controllers

import javax.inject.Inject

import play.api.mvc._
import uk.gov.hmrc.apidocumentation.models.DocumentationResource
import uk.gov.hmrc.apidocumentation.services.DocumentationService
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

class DocumentationController @Inject() (service: DocumentationService) extends BaseController {

  def fetchApiDocumentationResource(serviceName: String, version: String, resource: String) = Action.async { implicit request =>
    service.fetchApiDocumentationResource(serviceName, version, resource) map {
      case DocumentationResource(body, Some(contentType)) => Ok(body).withHeaders(CONTENT_TYPE -> contentType)
      case DocumentationResource(body, _) => Ok(body)
    }
  }
}
