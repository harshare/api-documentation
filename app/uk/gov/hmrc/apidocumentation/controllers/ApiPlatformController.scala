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

package uk.gov.hmrc.apidocumentation.controllers

import javax.inject.Inject

import controllers.AssetsBuilder
import play.api.http.HttpErrorHandler
import play.api.mvc.Action
import uk.gov.hmrc.apidocumentation.config.ServiceConfiguration
import uk.gov.hmrc.apidocumentation.models.ApiAccess
import uk.gov.hmrc.apidocumentation.models.ApiDefinition._
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.apidocumentation.views.txt

class ApiPlatformController  @Inject()(httpErrorHandler: HttpErrorHandler, config: ServiceConfiguration)
  extends AssetsBuilder(httpErrorHandler) with BaseController {

  def definition = Action {
    if(config.publishApiDefinition) {
      Ok(txt.definition(config.apiContext, ApiAccess.build(config.access))).withHeaders(CONTENT_TYPE -> JSON)
    } else {
      NotFound
    }
  }

  def raml(version: String, file: String) = Action {
    if(config.publishApiDefinition) {
      Ok(txt.application(config.apiContext))
    } else {
      NotFound
    }
  }
}