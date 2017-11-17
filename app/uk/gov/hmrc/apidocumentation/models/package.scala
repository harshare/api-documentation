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

package uk.gov.hmrc.apidocumentation

import play.api.libs.json._

package object EnumJson {

  def enumReads[E <: Enumeration](enum: E): Reads[E#Value] = new Reads[E#Value] {
    def reads(json: JsValue): JsResult[E#Value] = json match {
      case JsString(s) => {
        try {
          JsSuccess(enum.withName(s))
        } catch {
          case _: NoSuchElementException =>
            JsError(s"Enumeration expected of type: '${enum.getClass}', but it does not contain '$s'")
        }
      }
      case _ => JsError("String value expected")
    }
  }

  implicit def enumWrites[E <: Enumeration]: Writes[E#Value] = new Writes[E#Value] {
    def writes(v: E#Value): JsValue = JsString(v.toString)
  }

  implicit def enumFormat[E <: Enumeration](enum: E): Format[E#Value] = {
    Format(enumReads(enum), enumWrites)
  }
}

package object models {
  implicit val formatServiceDetails = Json.format[ServiceDetails]
  implicit val formatHttpMethod = EnumJson.enumFormat(HttpMethod)
  implicit val formatApiStatus = EnumJson.enumFormat(ApiStatus)
  implicit val formatApiAccessType = EnumJson.enumFormat(ApiAccessType)
  implicit val formatApiAccess = Json.format[ApiAccess]
  implicit val formatVersionVisibility = Json.format[VersionVisibility]
  implicit val formatParameter = Json.format[Parameter]
  implicit val formatEndpoint = Json.format[Endpoint]
  implicit val formatApiVersion = Json.format[ApiVersion]
  implicit val formatApiDefinition = Json.format[ApiDefinition]
  implicit val formatExtApiDefinition = Json.format[ExtendedApiDefinition]
}
