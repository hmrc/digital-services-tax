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
package util

import java.time.LocalDate

import enumeratum.scalacheck._
import cats.implicits.{none, _}
import org.scalacheck.Arbitrary.{arbitrary, arbBigDecimal => _, _}
import org.scalacheck.cats.implicits._
import org.scalacheck.{Arbitrary, Gen, _}
import uk.gov.hmrc.digitalservicestax.backend_data.RosmRegisterWithoutIDRequest
import uk.gov.hmrc.digitalservicestax.data.{AccountNumber, Activity, Address, BankAccount, Company, CompanyRegWrapper, ContactDetails, CountryCode, DSTRegNumber, DomesticBankAccount, Email, ForeignAddress, ForeignBankAccount, FormBundleNumber, GroupCompany, IBAN, InternalId, Money, NonEmptyString, Percent, Period, PhoneNumber, Postcode, RegexValidatedString, Registration, RepaymentDetails, Return, SafeId, SortCode, UTR, UkAddress}
import wolfendale.scalacheck.regexp.RegexpGen

object TestInstances {

  // these characters look very suspect to me
  implicit val arbString: Arbitrary[String] = Arbitrary(
    Gen.alphaNumStr.map{_.take(255)}
    //    RegexpGen.from("""^[0-9a-zA-Z{À-˿’}\\- &`'^._|]{1,255}$""")
  )


  // what range of values is acceptable? pennies? fractional pennies?
  implicit val arbMoney: Arbitrary[Money] = Arbitrary(
    Gen.choose(0L, Long.MaxValue).map{BigDecimal.apply}
  )


  implicit val arbPercent: Arbitrary[Percent] = Arbitrary {
    Gen.chooseNum(0, 100).map(b => Percent(b.toByte))
  }

  def nonEmptyString: Gen[NonEmptyString] =
    arbitrary[String].filter(_.nonEmpty).map{NonEmptyString.apply}

  implicit class RichRegexValidatedString[A <: RegexValidatedString](val in: A) {
    def gen = RegexpGen.from(in.regex).map{in.apply}
  }

  def genActivityPercentMap: Gen[Map[Activity, Percent]] = Gen.mapOf(
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
    genActivityPercentMap,
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

  def neString(maxLen: Int = 255) = (
    (
      Gen.alphaNumChar,
      arbitrary[String]
      ).mapN(_ + _).
      map{_.take(maxLen)}.map{NonEmptyString.apply}
    )

  implicit def arbNEString: Arbitrary[NonEmptyString] = Arbitrary { neString() }
  implicit def arbPostcode: Arbitrary[Postcode] = Arbitrary(Postcode.gen)
  implicit def arbDSTNumber: Arbitrary[DSTRegNumber] = Arbitrary(DSTRegNumber.gen)
  implicit def arbFormBundleNumber: Arbitrary[FormBundleNumber] = Arbitrary(FormBundleNumber.gen)
  implicit def arbCountryCode: Arbitrary[CountryCode] = Arbitrary(CountryCode.gen)
  implicit def arbPhone: Arbitrary[PhoneNumber] = Arbitrary(PhoneNumber.gen)
  implicit def arbUTR: Arbitrary[UTR] = Arbitrary(UTR.gen)
  implicit val arbInternalId: Arbitrary[InternalId] = Arbitrary(InternalId.gen)

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
        Gen.option(arbitrary[Address]),
        arbitrary[Option[Company]],
        arbitrary[ContactDetails],
        date(LocalDate.of(2039,1,1), LocalDate.of(2040,1,1)),
        arbitrary[LocalDate],
        Gen.some(arbitrary[DSTRegNumber])
        ).mapN(Registration.apply)
    }
  )

  implicit def genRegistration: Arbitrary[RosmRegisterWithoutIDRequest] = Arbitrary {
    (
      arbitrary[Boolean],
      arbitrary[Boolean],
      arbitrary[Company],
      arbitrary[ContactDetails]
      ).mapN(RosmRegisterWithoutIDRequest.apply)
  }


}