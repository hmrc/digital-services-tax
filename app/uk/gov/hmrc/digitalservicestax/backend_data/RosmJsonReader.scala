/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.digitalservicestax
package backend_data

import cats.implicits._
import play.api.libs.json._
import uk.gov.hmrc.digitalservicestax.data._

object RosmJsonReader extends Reads[CompanyRegWrapper] {

  object NotAnOrganisationException extends NoSuchElementException("Not an organisation")
  object InvalidCompanyNameException extends RuntimeException("Invalid company name")
  object InvalidAddressException extends RuntimeException("Invalid address")

  implicit val jaddress: Reads[Address] = new Reads[Address] {
    def reads(json: JsValue): JsResult[Address] = JsSuccess {
      try {
        { (json \ "countryCode").as[String] } match {
          case "GB"    =>
            UkAddress(
              { json \ "addressLine1" }.as[AddressLine],
              { json \ "addressLine2" }.asOpt[AddressLine],
              { json \ "addressLine3" }.asOpt[AddressLine],
              { json \ "addressLine4" }.asOpt[AddressLine],
              { json \ "postalCode" }.as[Postcode]
            )
          case country =>
            ForeignAddress(
              { json \ "addressLine1" }.as[AddressLine],
              { json \ "addressLine2" }.asOpt[AddressLine],
              { json \ "addressLine3" }.asOpt[AddressLine],
              { json \ "addressLine4" }.asOpt[AddressLine],
              CountryCode(country)
            )
        }
      } catch {
        case e: JsResultException => throw InvalidAddressException
      }
    }
  }

  def oreads(json: JsObject): JsResult[CompanyRegWrapper] = {

    if ({ json \ "organisation" }.isEmpty) {
      throw NotAnOrganisationException
    }

    try {
      { json \ "organisation" \ "organisationName" }.as[CompanyName]
    } catch {
      case e: JsResultException => throw InvalidCompanyNameException
    }

    JsSuccess(
      CompanyRegWrapper(
        Company(
          { json \ "organisation" \ "organisationName" }.as[CompanyName],
          { json \ "address" }.as[Address]
        ),
        safeId = SafeId(
          { json \ "safeId" }.as[String]
        ).some,
        sapNumber = {
          json \ "sapNumber"
        }.asOpt[SapNumber]
      )
    )
  }

  def reads(json: JsValue): JsResult[CompanyRegWrapper] =
    json match {
      case o: JsObject => oreads(o)
      case x           => JsError(s"expected an object, found $x")
    }
}
