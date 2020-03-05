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
package services

import data._
import org.scalatest.{FlatSpec, Matchers}

import java.time.LocalDate
import org.scalatestplus.scalacheck._
import org.scalacheck.cats.implicits._
import cats.implicits._
import cats.Applicative
import org.scalacheck._, Arbitrary.{arbBigDecimal => _, _}
import enumeratum.scalacheck._
import shapeless.tag.@@
import wolfendale.scalacheck.regexp.RegexpGen

class DstReturnSchemaSpec extends FlatSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  // these characters look very suspect to me 
  implicit val arbString: Arbitrary[String] = Arbitrary(
    Gen.alphaNumStr.map{_.take(255)}
//    RegexpGen.from("""^[0-9a-zA-Z{À-˿’}\\- &`'^._|]{1,255}$""")
  )

  // what range of values is acceptable? pennies? fractional pennies?
  implicit val arbMoney: Arbitrary[Money] = Arbitrary(
    Gen.choose(0L, Long.MaxValue).map{BigDecimal.apply}
  )


  def nonEmptyString: Gen[NonEmptyString] =
    arbitrary[String].filter(_.nonEmpty).map{NonEmptyString.apply}

  implicit class RichRegexValidatedString[A <: RegexValidatedString](val in: A) {
    def gen = RegexpGen.from(in.regex).map{in.apply}
  }

  def genMap: Gen[Map[Activity, Percent]] = Gen.mapOf(
    (
      arbitrary[Activity],
      Gen.choose(0,100).map{x => Percent.apply(x.asInstanceOf[Byte])}
    ).tupled
  )

  def genGroupCo: Gen[GroupCompany] = (
    nonEmptyString,
    Gen.option(UTR.gen)
  ).mapN(GroupCompany.apply)

  def gencomap: Gen[Map[GroupCompany, Money]] = Gen.mapOf(
    (
      genGroupCo,
      arbitrary[Money]
    ).tupled
  )

  def genBankAccount: Gen[BankAccount] = {

    val genDomestic: Gen[DomesticBankAccount] = (
      SortCode.gen,
      AccountNumber.gen,
      arbitrary[String]
    ).mapN(DomesticBankAccount.apply)

    val genForeign: Gen[ForeignBankAccount] =
      IBAN.gen.map{ForeignBankAccount.apply}
    Gen.oneOf(genDomestic, genForeign)
  }

  def genRepayment: Gen[RepaymentDetails] = 
    (
      nonEmptyString,
      genBankAccount
    ).mapN(RepaymentDetails.apply)

  def date(start: LocalDate, end: LocalDate): Gen[LocalDate] = 
    Gen.choose(start.toEpochDay, end.toEpochDay).map(LocalDate.ofEpochDay)

  implicit def returnGen: Arbitrary[Return] = Arbitrary((
    genMap,
    arbitrary[Money],
    gencomap,
    arbitrary[Money],
    arbitrary[Money],
    Gen.option(genRepayment)
  ).mapN(Return.apply))

  implicit def arbDate: Arbitrary[LocalDate] = Arbitrary(
    date(LocalDate.of(2010, 1, 1), LocalDate.of(2020, 1, 1))
  )

  implicit def periodArb: Arbitrary[Period] = Arbitrary((
    arbitrary[LocalDate],
    arbitrary[LocalDate],
    arbitrary[LocalDate],
    arbitrary[NonEmptyString].map{_.take(4)}.map{Period.Key(_)}
  ).mapN(Period.apply))

  "A return API call" should "conform to the schema" in {
    forAll { (period: Period, ret: Return) => 
      val json = EeittInterface.returnRequestWriter("aoeu",period).writes(ret)
      SchemaChecker.EeittReturn.request.errorsIn(json) should be (None)
    }
  }

  def neString(maxLen: Int = 255) = (
    (
      Gen.alphaNumChar,
      arbitrary[String]
    ).mapN(_ + _).
      map{_.take(maxLen)}.map{NonEmptyString.apply}
  )

  implicit def arbNEString: Arbitrary[NonEmptyString] = Arbitrary { neString() }
  implicit def arbPostcode: Arbitrary[Postcode] = Arbitrary(Postcode.gen)
  implicit def arbCountryCode: Arbitrary[CountryCode] = Arbitrary(CountryCode.gen)
  implicit def arbPhone: Arbitrary[PhoneNumber] = Arbitrary(PhoneNumber.gen)
  implicit def arbUTR: Arbitrary[UTR] = Arbitrary(UTR.gen)  

  // note this does NOT check all RFC-compliant email addresses (e.g. '"luke tebbs"@company.co.uk')
  implicit def arbEmail: Arbitrary[Email] = Arbitrary{
    (
      neString(20),
      neString(20)
    ).mapN((a,b) => Email(s"${a}@${b}.co.uk"))
  }

  implicit def arbAddr: Arbitrary[Address] = Arbitrary {

    val ukGen: Gen[Address] = (
      neString(40),
      arbitrary[String].map{_.take(40)},
      arbitrary[String].map{_.take(40)},
      arbitrary[String].map{_.take(40)},
      arbitrary[Postcode] 
    ).mapN(UkAddress.apply)

    val foreignGen: Gen[Address] = (
      neString(40),
      arbitrary[String].map{_.take(40)},
      arbitrary[String].map{_.take(40)},
      arbitrary[String].map{_.take(40)},
      arbitrary[String].map{_.take(40)},       
      arbitrary[Postcode],
      arbitrary[CountryCode]
    ).mapN(ForeignAddress)

    Gen.oneOf(ukGen, foreignGen)

  }

  implicit def arbCo: Arbitrary[Company] = Arbitrary(
    (
      arbitrary[NonEmptyString],
      arbitrary[Address]
    ).mapN(Company.apply)
  )

  implicit def arbCoRegWrap: Arbitrary[CompanyRegWrapper] = Arbitrary(
    (
      arbitrary[Company],
      Gen.const(none[UTR]),
      Gen.const(none[SafeId]),
      Gen.const(false)
    ).mapN(CompanyRegWrapper.apply)
  )

  implicit def arbContact: Arbitrary[ContactDetails] = Arbitrary {
    (
      neString(40),
      neString(40),
      arbitrary[PhoneNumber],
      arbitrary[Email]        
    ).mapN(ContactDetails.apply)
  }

  implicit def subGen: Arbitrary[Registration] = Arbitrary (
    {
      (
        arbitrary[CompanyRegWrapper],
        arbitrary[Option[Address]],
        arbitrary[Option[Company]],
        arbitrary[ContactDetails],
        date(LocalDate.of(2039,1,1), LocalDate.of(2040,1,1)),
        arbitrary[LocalDate],
        Gen.const(none[DSTRegNumber])
      ).mapN(Registration.apply)
    }
  )

  "A registration API call" should "conform to the schema" in {
    forAll { (subRequest: Registration) => 
      val json = EeittInterface.registrationWriter.writes(subRequest)
      SchemaChecker.EeittSubscribe.request.errorsIn(json) shouldBe (None)
    }
  }

    // val r = SubscriptionRequest(
    //   UnknownIdentification :: Nil,
    //   CustomerData("", "", Honorific.Mr, "Jennifer", "Alison", LocalDate.of(1989, 6, 10)), 
    //   NominatedCompany(ForeignAddress("Address Line 111", "Address Line 222", "Address Line 333", "Address Line 444", "Business Address Line 5", "", "DE"), "", "test@dstsystemtest.com"), 
    //   ContactDetails("Pascalle", "00447800399022", "pascalle@frenchparliament.com"),
    //   UltimateOwner("Group Holdings for DST", "GLOBAL ID 11223344556677", ForeignAddress("French Parliament Building", "La Chapelle", "Cheaveux", "Paris", "Correspondence Address Line 5", "", "FR")),
    //   (LocalDate.now, LocalDate.now)
    // )

  //   val json = EeittInterface.subscriptionRequestWriter.writes(r)
  //   import play.api.libs.json._
  //   println(Json.prettyPrint(json))
  //   SchemaChecker.EeittSubscribe.request.errorsIn(json) shouldBe (None)
  // }
  
}
