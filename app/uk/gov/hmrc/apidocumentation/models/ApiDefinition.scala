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

package uk.gov.hmrc.apidocumentation.models

import play.api.Configuration
import uk.gov.hmrc.apidocumentation.models.ApiCategory.ApiCategory
import uk.gov.hmrc.apidocumentation.models.ApiStatus.ApiStatus
import uk.gov.hmrc.apidocumentation.models.HttpMethod.HttpMethod

object ApiAccessType extends Enumeration {
  type ApiAccessType = Value
  val PRIVATE, PUBLIC = Value
}

case class ApiAccess(`type`: ApiAccessType.Value, whitelistedApplicationIds: Option[Seq[String]], isTrial: Option[Boolean] = None)

object ApiAccess {
  def build(config: Option[Configuration]): ApiAccess = ApiAccess(
    `type` = ApiAccessType.PRIVATE,
    whitelistedApplicationIds = config.flatMap(_.getStringSeq("whitelistedApplicationIds")).orElse(Some(Seq.empty)),
    isTrial = None)
}
object ApiStatus extends Enumeration {
  type ApiStatus = Value
  val ALPHA, BETA, PROTOTYPED, PUBLISHED, STABLE, DEPRECATED, RETIRED = Value
}

object ApiCategory extends Enumeration {
  type ApiCategory = Value

  val EXAMPLE, AGENTS, CORPORATION_TAX, CUSTOMS, ESTATES, HELP_TO_SAVE, INCOME_TAX_MTD,
    LIFETIME_ISA, MARRIAGE_ALLOWANCE, NATIONAL_INSURANCE, PAYE, PENSIONS, PRIVATE_GOVERNMENT,
    RELIEF_AT_SOURCE, SELF_ASSESSMENT, STAMP_DUTY, TRUSTS, VAT, VAT_MTD, OTHER = Value
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
                         versions: Seq[ApiVersion],
                         categories: Option[Seq[ApiCategory]] = None) {
  def isIn(definitions: Seq[ApiDefinition]): Boolean = {
    definitions.map(_.name).contains(name)
  }
}

case class ApiVersion(version: String, access: Option[ApiAccess], status: ApiStatus, endpoints: Seq[Endpoint], endpointsEnabled: Boolean)

case class Endpoint(endpointName: String, uriPattern: String, method: HttpMethod, queryParameters: Option[Seq[Parameter]] = None)

case class ExtendedApiDefinition(serviceName: String,
                                 serviceBaseUrl: String,
                                 name: String,
                                 description: String,
                                 context: String,
                                 requiresTrust: Boolean,
                                 isTestSupport: Boolean,
                                 versions: Seq[ExtendedApiVersion])

case class ExtendedApiVersion(version: String,
                              status: ApiStatus,
                              endpoints: Seq[Endpoint],
                              productionAvailability: Option[ApiAvailability],
                              sandboxAvailability: Option[ApiAvailability])

case class ApiAvailability(endpointsEnabled: Boolean, access: ApiAccess, loggedIn: Boolean, authorised: Boolean)
