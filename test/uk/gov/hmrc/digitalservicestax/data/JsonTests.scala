/*
 * Copyright 2021 HM Revenue & Customs
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

import java.time.LocalDate
import com.outworkers.util.samplers._
import enumeratum.scalacheck._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json._
import uk.gov.hmrc.digitalservicestax.backend_data.RosmRegisterWithoutIDRequest
import uk.gov.hmrc.digitalservicestax.data
import uk.gov.hmrc.digitalservicestax.data.BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.services.{EeittInterface, JsonSchemaChecker}
import uk.gov.hmrc.digitalservicestax.util.TestInstances
import uk.gov.hmrc.digitalservicestax.util.TestInstances._

import scala.collection.immutable.ListMap
import scala.language.postfixOps

class JsonTests extends FlatSpec
  with Matchers
  with ScalaCheckDrivenPropertyChecks
  with EitherValues
  with OptionValues {

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

  it should "purge none and empty values from a map of js values" in {
    val generated = JsString(gen[ShortString].value)
    val source = JsObject(Seq(
      "object_example" -> JsObject(Seq("bla" -> generated)),
      "null-example" -> JsNull,
      "empty-string-example" -> JsString(""),
      "good-example" -> JsString("good")
    ))

    val res = RosmRegisterWithoutIDRequest.purgeNullAndEmpty(source)
    res.value should contain theSameElementsAs (JsObject(Seq(
      "object_example" -> JsObject(Seq("bla" -> generated)),
      "good-example" -> JsString("good")
    )).value)
  }

  it should "fail to validate a postcode from JSON if the source input doesn't match expected regex" in {
    val parsed = Json.parse(s""" "124124125125125" """).validate[Postcode]
    parsed.isSuccess shouldEqual false
    parsed shouldEqual JsError(s"Expected a valid postcode, got 124124125125125 instead")
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

  it should "send the ultimateParent name for A_DST_GLOBAL_NAME field in Reg writer" in {
    implicit val arbRegWithParent: Arbitrary[Registration] = TestInstances.subGenWithParent
    forAll { (reg: Registration) =>
      val regJson: JsValue = Json.toJson(reg)(EeittInterface.registrationWriter)
      val params = (regJson \ "registrationDetails" \ "regimeSpecificDetails").as[JsArray].value.toList
      val param: JsValue = params.filter(js => (js \ "paramName").get.as[String] == "A_DST_GLOBAL_NAME").head
      val name = (param \ "paramValue").get.as[String]
      name shouldEqual reg.ultimateParent.get.name
    }(implicitly, arbRegWithParent, implicitly, implicitly, implicitly, implicitly)
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
    testJsonRoundtrip[ListMap[GroupCompany, Money]](gencomap)
  }

  it should "serialize and de-serialise a LocalDate" in {
    testJsonRoundtrip[LocalDate]
  }

  it should "fail to parse an invalid LocalDate" in {
    val source = JsString(gen[ShortString].value)
    val expectionSpec = implicitly[Format[LocalDate]].reads(source)
    val lastError = JsError(expectionSpec.asEither.left.value)

    val jsError = JsError(
      List(
        (JsPath, List(JsonValidationError(List(s"Text '${source.value}' could not be parsed at index 0"))))
      )
    )

    lastError shouldBe jsError
  }

  it should "serialize and de-serialise an optional LocalDate" in {
    testJsonRoundtrip[Option[LocalDate]]
  }

  it should "serialize and de-serialise a Map[Activity, Percent]" in {
    testJsonRoundtrip[Map[Activity, Percent]](genActivityPercentMap)
  }


  it should "serialize and de-serialise a CompanyRegFormat" in {
    testJsonRoundtrip[CompanyRegWrapper]
  }

  it should "serialize an enum entry as a string" in {
    val jsValue = Json.toJson(Activity.SocialMedia)
    jsValue shouldEqual JsString("SocialMedia")
  }

  it should "test the JSON schema for Company" in {
    forAll { company: Company =>
      JsonSchemaChecker[data.Company](company, "rosm-response")
    }
  }


  it should "serialize a scala.Unit" in {
    testJsonRoundtrip[Unit](Gen.const(()))
  }

  it should "fail to parse a scala.Unit from a non JsNull JSON source" in {
    val jsstr = JsString("abasgjas")
    unitFormat.reads(jsstr) shouldBe JsError(s"expected JsNull, encountered $jsstr")
  }

  it should "serialize a random option format" in {
    testJsonRoundtrip[Option[NonEmptyString]](Gen.option(nonEmptyString))
  }

  it should "test the JSON schema for registratiom" in {
    forAll { reg: Registration =>
      JsonSchemaChecker[data.Registration](reg, "rosm-response")
    }
  }
}
