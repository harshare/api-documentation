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

package uk.gov.hmrc.apidocumentation.models

import uk.gov.hmrc.apidocumentation.models.ApiStatus.ApiStatus
import uk.gov.hmrc.apidocumentation.models.HttpMethod.HttpMethod

object ApiAccessType extends Enumeration {
  type ApiAccessType = Value
  val PRIVATE, PUBLIC = Value
}

case class ApiAccess(`type`: ApiAccessType.Value)

object ApiStatus extends Enumeration {
  type ApiStatus = Value
  val ALPHA, BETA, PROTOTYPED, PUBLISHED, STABLE, DEPRECATED, RETIRED = Value
}


object HttpMethod extends Enumeration {
  type HttpMethod = Value
  val GET, POST, PUT, DELETE, OPTIONS = Value
}

case class Parameter(name: String, required: Boolean = false)

case class ApiDefinition(serviceName: String,
                         name: String,
                         description: String,
                         context: String,
                         requiresTrust: Option[Boolean],
                         isTestSupport: Option[Boolean],
                         versions: Seq[ApiVersion])

case class ApiVersion(version: String, access: Option[ApiAccess], status: ApiStatus, endpoints: Seq[Endpoint])

case class ExtendedApiDefinition(apiDefinition: ApiDefinition, versionAccess: Map[String, VersionVisibility])

case class VersionVisibility(privacy: ApiAccessType.ApiAccessType, loggedIn: Boolean, authorised: Boolean)

case class Endpoint(endpointName: String, uriPattern: String, method: HttpMethod, queryParameters: Option[Seq[Parameter]] = None)
