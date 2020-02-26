/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.Instant

import play.api.libs.json._
import uk.gov.hmrc.digitalservicestax.data._

object RosmJsonWriter extends Writes[RosmRegisterWithoutIDRequest] {

  override def writes(o: RosmRegisterWithoutIDRequest): JsValue = {
    Json.obj(
      "acknowledgementReference" -> AcknowledgementReference.generate(o.organisation.address.postalCode),
      "regime" -> "DST",
      "isAnAgent" -> o.isAnAgent,
      "isAGroup" -> o.isAGroup,
      "organisation" -> Json.obj(
        "organisationName" -> o.organisation.name
      ),
      "address" -> Json.obj(
        "addressLine1" -> o.organisation.address.line1,
        "addressLine2" -> o.organisation.address.line2,
        "addressLine3" -> o.organisation.address.line3,
        "addressLine4" -> o.organisation.address.line4,
        "postalCode" -> o.organisation.address.postalCode,
        "countryCode" -> o.organisation.address.countryCode
      ),
      "contactDetails" -> Json.obj(
        "phoneNumber" -> o.contactDetails.phoneNumber,
        "emailAddress" -> o.contactDetails.email
      )
    )
  }
}

object AcknowledgementReference {
  def generate(postcode: String): String =
    postcode.map(_.toInt).mkString +
    Instant.now.toEpochMilli.toString.slice(0,32)

}