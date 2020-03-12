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

import org.scalatest.{FlatSpec, Matchers}

class RosmJsonReaderSpec extends FlatSpec with Matchers {


  it should "fail to parse an empty organisation" in {
    val json: JsValue = Json.parse(
      """|{
         |  "safeId": "XE0001234567890",
         |  "sapNumber": "1234567890",
         |  "agentReferenceNumber": "AARN1234567",
         |  "isEditable": true,
         |  "isAnAgent": false,
         |  "isAnASAgent":false,
         |  "isAnIndividual": true,
         |  "organisation": null,
         |  "address": {
         |    "addressLine1": "100 SuttonStreet",
         |    "addressLine2": "Wokingham",
         |    "addressLine3": "Surrey",
         |    "addressLine4": "London",
         |    "postalCode": "DH14 1EJ",
         |    "countryCode": "GB"
         |  },
         |  "contactDetails": {
         |    "primaryPhoneNumber": "01332752856",
         |    "secondaryPhoneNumber": "07782565326",
         |    "faxNumber": "01332754256",
         |    "emailAddress": "stephen@manncorpone.co.uk"
         |  }
         |}
         |""".stripMargin)

    intercept[JsResultException] {
      RosmJsonReader.reads(json).isSuccess shouldEqual false
    }
  }

  it should "parse a ForeignAddress from a JSON object if the country code in the source JSON is not GB" in {
    val json: JsValue = Json.parse(
    """|{
       |  "safeId": "XE0001234567890",
       |  "sapNumber": "1234567890",
       |  "agentReferenceNumber": "AARN1234567",
       |  "isEditable": true,
       |  "isAnAgent": false,
       |  "isAnASAgent":false,
       |  "isAnIndividual": true,
       |  "organisation": {
       |    "organisationName": "Trotters Trading (Stepney) Ltd.",
       |    "isAGroup": true,
       |    "organisationType": "Not Specified"
       |  },
       |  "address": {
       |    "addressLine1": "Sky, 33R",
       |    "addressLine2": "605 West 42nd Street",
       |    "addressLine3": "New York",
       |    "addressLine4": "",
       |    "postalCode": "10036",
       |    "countryCode": "US"
       |  },
       |  "contactDetails": {
       |    "primaryPhoneNumber": "01332752856",
       |    "secondaryPhoneNumber": "07782565326",
       |    "faxNumber": "01332754256",
       |    "emailAddress": "stephen@manncorpone.co.uk"
       |  }
       |}
       |""".stripMargin)

    RosmJsonReader.reads(json) shouldEqual JsSuccess(
      CompanyRegWrapper(
        Company(
          NonEmptyString("Trotters Trading (Stepney) Ltd."),
          ForeignAddress(
            line1 = NonEmptyString("Sky, 33R"),
            line2 = "605 West 42nd Street",
            line3 = "New York",
            line4 = "",
            line5 = "",
            postalCode = "10036",
            CountryCode("US")
          )
        ),
        None,
        Some(SafeId("XE0001234567890"))
      )
    )
  }

  val json: JsValue = Json.parse(
    """|{
       |  "safeId": "XE0001234567890",
       |  "sapNumber": "1234567890",
       |  "agentReferenceNumber": "AARN1234567",
       |  "isEditable": true,
       |  "isAnAgent": false,
       |  "isAnASAgent":false,
       |  "isAnIndividual": true,
       |  "organisation": {
       |    "organisationName": "Trotters Trading (Stepney) Ltd.",
       |    "isAGroup": true,
       |    "organisationType": "Not Specified"
       |  },
       |  "address": {
       |    "addressLine1": "100 SuttonStreet",
       |    "addressLine2": "Wokingham",
       |    "addressLine3": "Surrey",
       |    "addressLine4": "London",
       |    "postalCode": "DH14 1EJ",
       |    "countryCode": "GB"
       |  },
       |  "contactDetails": {
       |    "primaryPhoneNumber": "01332752856",
       |    "secondaryPhoneNumber": "07782565326",
       |    "faxNumber": "01332754256",
       |    "emailAddress": "stephen@manncorpone.co.uk"
       |  }
       |}
       |""".stripMargin)

  "a rosm response" should "be readable" in {
    RosmJsonReader.reads(json) should be (JsSuccess(
      CompanyRegWrapper(
        Company(
          NonEmptyString("Trotters Trading (Stepney) Ltd."),
          UkAddress(
            NonEmptyString("100 SuttonStreet"),
            "Wokingham",
            "Surrey",
            "London",
            Postcode("DH14 1EJ")
          )
        ),
        None,
        Some(SafeId("XE0001234567890"))
      )
    ))
  }
}
