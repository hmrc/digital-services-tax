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

package uk.gov.hmrc.digitalservicestax
package backend

import play.api.libs.json._
import data._

object RosmJsonReader extends Reads[Company] {

  object NotAnOrganisationException extends NoSuchElementException("Not an organisation")

  implicit val jaddress = new Reads[Address] {
    def reads(json: JsValue): JsResult[Address] = JsSuccess{
      {(json \ "countryCode").as[String]} match {
        case "GB" => UkAddress(
          {json \ "addressLine1"}.as[NonEmptyString],
          {json \ "addressLine2"}.asOpt[String].getOrElse(""),
          {json \ "addressLine3"}.asOpt[String].getOrElse(""),
          {json \ "addressLine4"}.asOpt[String].getOrElse(""),
          {json \ "postalCode"}.as[Postcode]
        )
        case country => ForeignAddress(
          {json \ "addressLine1"}.as[NonEmptyString],
          {json \ "addressLine2"}.asOpt[String].getOrElse(""),
          {json \ "addressLine3"}.asOpt[String].getOrElse(""),
          {json \ "addressLine4"}.asOpt[String].getOrElse(""),
          {json \ "addressLine5"}.asOpt[String].getOrElse(""),
          {json \ "postalCode"}.as[String],
          CountryCode(country)
        )
      }
    }
  }

  def oreads(json: JsObject): JsResult[Company] = {

    if ({json \ "organisation"}.isEmpty) {
      throw NotAnOrganisationException
    }

    JsSuccess(Company(
      {json \ "organisation" \ "organisationName"}.as[NonEmptyString],
      {json \ "address"}.as[Address]
    ))
  }

  def reads(json: JsValue): JsResult[Company] = json match {
    case o: JsObject => oreads(o)
    case x => JsError(s"expected an object, found $x")
  }
}
