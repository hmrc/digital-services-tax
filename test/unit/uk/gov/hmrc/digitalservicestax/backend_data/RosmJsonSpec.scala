/*
 * Copyright 2022 HM Revenue & Customs
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

package unit.uk.gov.hmrc.digitalservicestax.backend_data

import cats.implicits._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.{Assertion, OptionValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json._
import uk.gov.hmrc.digitalservicestax.backend_data.{RosmJsonReader, RosmRegisterWithoutIDRequest}
import uk.gov.hmrc.digitalservicestax.backend_data.RosmJsonReader.NotAnOrganisationException
import uk.gov.hmrc.digitalservicestax.data._
import unit.uk.gov.hmrc.digitalservicestax.util.TestInstances._

class RosmJsonSpec extends AnyFlatSpec with Matchers with ScalaCheckDrivenPropertyChecks with OptionValues {

  def testJsonRoundtrip[T: Arbitrary: Format]: Assertion =
    forAll { sample: T =>
      val js = Json.toJson(sample)

      val parsed = js.validate[T]
      parsed.isSuccess   shouldEqual true
      parsed.asOpt.value shouldEqual sample
    }

  def testJsonRoundtrip[T: Format](gen: Gen[T]): Assertion =
    forAll(gen) { sample: T =>
      val js = Json.toJson(sample)

      val parsed = js.validate[T]
      parsed.isSuccess   shouldEqual true
      parsed.asOpt.value shouldEqual sample
    }

  it should "fail to parse a non JSObject" in {
    val json = Json.parse("null");
    RosmJsonReader.reads(json) shouldBe a[JsError]
  }

  it should "fail to parse an empty organisation" in {
    val json: JsValue = Json.parse("""|{
         |  "safeId": "XE0001234567890",
         |  "sapNumber": "1234567890",
         |  "agentReferenceNumber": "AARN1234567",
         |  "isEditable": true,
         |  "isAnAgent": false,
         |  "isAnASAgent":false,
         |  "isAnIndividual": true,
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

    intercept[NotAnOrganisationException.type] {
      RosmJsonReader.reads(json).isSuccess shouldEqual false
    }
  }

  it should "parse a ForeignAddress from a JSON object if the country code in the source JSON is not GB" in {
    val json: JsValue = Json.parse("""|{
       |  "safeId": "XE0001234567890",
       |  "sapNumber": "1234567890",
       |  "agentReferenceNumber": "AARN1234567",
       |  "isEditable": true,
       |  "isAnAgent": false,
       |  "isAnASAgent":false,
       |  "isAnIndividual": true,
       |  "organisation": {
       |    "organisationName": "Trotters Trading",
       |    "isAGroup": true,
       |    "organisationType": "Not Specified"
       |  },
       |  "address": {
       |    "addressLine1": "Sky 33R",
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
          CompanyName("Trotters Trading"),
          ForeignAddress(
            line1 = AddressLine("Sky 33R"),
            line2 = AddressLine("605 West 42nd Street").some,
            line3 = AddressLine("New York").some,
            line4 = None,
            CountryCode("US")
          )
        ),
        None,
        Some(SafeId("XE0001234567890"))
      )
    )
  }

  val json: JsValue = Json.parse("""|{
       |  "safeId": "XE0001234567890",
       |  "sapNumber": "1234567890",
       |  "agentReferenceNumber": "AARN1234567",
       |  "isEditable": true,
       |  "isAnAgent": false,
       |  "isAnASAgent":false,
       |  "isAnIndividual": true,
       |  "organisation": {
       |    "organisationName": "Trotters Trading",
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
    RosmJsonReader.reads(json) should be(
      JsSuccess(
        CompanyRegWrapper(
          Company(
            CompanyName("Trotters Trading"),
            UkAddress(
              AddressLine("100 SuttonStreet"),
              AddressLine("Wokingham").some,
              AddressLine("Surrey").some,
              AddressLine("London").some,
              Postcode("DH14 1EJ")
            )
          ),
          None,
          Some(SafeId("XE0001234567890"))
        )
      )
    )
  }

  it                should "serialize and deserialize a Rosm object to and from JSON" in {
    val parsed = RosmJsonReader.reads(json)
    parsed.isSuccess shouldEqual true

    forAll { value: RosmRegisterWithoutIDRequest =>
      Json.toJson(value)
    }
  }
}
