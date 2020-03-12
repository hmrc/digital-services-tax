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

    val invalidFormatString = Gen.alphaNumStr

    forAll(invalidFormatString) { sample =>
      val parsed = Json.parse(sample.toString).validate[Percent]
      
      parsed shouldEqual JsError(
        JsPath -> JsonValidationError(Seq(s"""Expected a valid percentage, got $sample instead"""))
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

  ignore should "serialize and de-serialise a Map[GroupCompany, Money]" in {
    testJsonRoundtrip[Map[GroupCompany, Money]](gencomap)
  }

  it should "serialize and de-serialise a Map[Activity, Percent]" in {
    testJsonRoundtrip[Map[Activity, Percent]](genMap)
  }
}
