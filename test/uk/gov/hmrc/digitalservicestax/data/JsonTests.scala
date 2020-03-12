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

package uk.gov.hmrc.digitalservicestax.data

import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.{Assertion, FlatSpec, Matchers, OptionValues}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.{Format, JsError, JsPath, JsResult, Json, JsonValidationError}
import uk.gov.hmrc.digitalservicestax.TestInstances._
import BackendAndFrontendJson._
import com.outworkers.util.samplers._
import enumeratum.scalacheck._

class JsonTests extends FlatSpec with Matchers with ScalaCheckDrivenPropertyChecks with OptionValues {

  def testJsonRoundtrip[T : Arbitrary : Format]: Assertion = {
    forAll { sample: T =>
      val js = Json.toJson(sample)

      val parsed = js.validate[T]
      parsed.isSuccess shouldEqual true
      parsed.asOpt.value shouldEqual sample
    }
  }


  def testJsonRoundtrip[T : Format](gen: Gen[T]): Assertion = {
    forAll(gen) { sample: T =>
      val js = Json.toJson(sample)

      val parsed = js.validate[T]
      parsed.isSuccess shouldEqual true
      parsed.asOpt.value shouldEqual sample
    }
  }

  it should "serialize and de-serialise a Postcode instance" in {
    testJsonRoundtrip[Postcode]
  }

  it should "fail to validate a postcode from JSON if the source input doesn't match expected regex" in {
    val generated = gen[ShortString].value
    val parsed = Json.parse(s""" "$generated" """).validate[Postcode]
    parsed.isSuccess shouldEqual false
    parsed shouldEqual JsError(s"Expected a valid postcode, got $generated instead")
  }


  it should "fail to validate a postcode from JSON if the source input is in incorrect format" in {
    val generated = gen[Int]
    val parsed = Json.parse(s"""$generated""").validate[Postcode]
    parsed.isSuccess shouldEqual false
    parsed shouldEqual JsError(JsPath -> JsonValidationError(Seq(s"""Expected a valid postcode, got $generated instead""")))
  }

  it should "serialize and de-serialise a PhoneNumber instance" in {
    testJsonRoundtrip[PhoneNumber]
  }


  it should "serialize and de-serialise a NonEmptyString instance" in {
    testJsonRoundtrip[NonEmptyString]
  }

  it should "fail to validate a NonEmptyString from an invalid source" in {
    forAll(Gen.chooseNum(Int.MinValue, Int.MaxValue)) { sample =>
      val parsed = Json.parse(sample.toString).validate[NonEmptyString]

      parsed shouldEqual JsError(
        (JsPath \ "value") -> JsonValidationError(Seq(s"Expected non empty string, got $sample"))
      )
    }
  }

  it should "serialize and de-serialise an Email instance" in {
    testJsonRoundtrip[Email]
  }

  it should "serialize and de-serialise a CountryCode instance" in {
    testJsonRoundtrip[CountryCode]
  }

  it should "serialize and de-serialise a UTR instance" in {
    testJsonRoundtrip[UTR]
  }

  it should "serialize and de-serialise a percent instance" in {
    testJsonRoundtrip[Percent]
  }

  it should "fail to parse a percent from a non 0 - 100 int value" in {

    val invalidPercentages = Gen.chooseNum(-100, -1)

    forAll(invalidPercentages) { sample =>
      val parsed = Json.parse(sample.toString).validate[Percent]
      parsed shouldEqual JsError(s"Expected a valid percentage, got $sample instead.")
    }
  }

  it should "fail to validate a percentage from a non numeric value" in {
    forAll(Sample.generator[ShortString]) { sample =>

      val parsed = Json.parse(s""" "${sample.value}" """).validate[Percent]

      parsed shouldEqual JsError(
        JsPath -> JsonValidationError(Seq(s"""Expected a valid percentage, got "${sample.value}" instead"""))
      )
    }
  }

  it should "serialize and de-serialise a GroupCompany instance" in {
    testJsonRoundtrip[GroupCompany](genGroupCo)
  }

  it should "serialize and de-serialise a Money instance" in {
    testJsonRoundtrip[Money]
  }

  it should "serialize and de-serialise an Activity instance" in {
    testJsonRoundtrip[Activity]
  }

  it should "serialize and de-serialise a Map[GroupCompany, Money]" in {
    testJsonRoundtrip[Map[GroupCompany, Money]](gencomap)
  }

  it should "serialize and de-serialise a Map[Activity, Percent]" in {
    testJsonRoundtrip[Map[Activity, Percent]](genActivityPercentMap)
  }
//
//  it should "serialize and de-serialise a DomesticBankAccount instance" in {
//    testJsonRoundtrip[DomesticBankAccount]
//  }
}
