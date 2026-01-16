/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.digitalservicestax.backend_data

import play.api.libs.json._
import uk.gov.hmrc.digitalservicestax.data.{Company, ContactDetails, _}

case class RosmRegisterWithoutIDRequest(
  isAnAgent: Boolean = false,
  isAGroup: Boolean = false,
  organisation: Company,
  contactDetails: ContactDetails
)

object RosmRegisterWithoutIDRequest {

  def purgeNullAndEmpty(json: JsObject): JsObject = json match {
    case JsObject(inner) =>
      val data: Map[String, JsValue] = (inner collect {
        case (k, o: JsObject)                      => Some(k -> purgeNullAndEmpty(o))
        case (_, JsNull)                           => None
        case (_, JsString(value)) if value.isEmpty => None
        case (k, other)                            => Some(k -> other)
      }).flatten.toMap
      JsObject(data)
  }

  implicit object RosmJsonWriter extends Writes[RosmRegisterWithoutIDRequest] {
    private def address(o: RosmRegisterWithoutIDRequest): JsValue =
      purgeNullAndEmpty(
        Json.obj(
          "addressLine1" -> o.organisation.address.line1,
          "addressLine2" -> o.organisation.address.line2,
          "addressLine3" -> o.organisation.address.line3,
          "addressLine4" -> o.organisation.address.line4,
          "postalCode"   -> o.organisation.address.postalCode,
          "countryCode"  -> o.organisation.address.countryCode
        )
      )

    override def writes(o: RosmRegisterWithoutIDRequest): JsValue =
      Json.obj(
        "acknowledgementReference" -> AcknowledgementReference.generate(o.organisation.address.postalCode),
        "regime"                   -> "DST",
        "isAnAgent"                -> o.isAnAgent,
        "isAGroup"                 -> o.isAGroup,
        "organisation"             -> Json.obj(
          "organisationName" -> o.organisation.name
        ),
        "address"                  -> address(o),
        "contactDetails"           -> Json.obj(
          "phoneNumber"  -> o.contactDetails.phoneNumber,
          "emailAddress" -> o.contactDetails.email
        )
      )
  }
}
