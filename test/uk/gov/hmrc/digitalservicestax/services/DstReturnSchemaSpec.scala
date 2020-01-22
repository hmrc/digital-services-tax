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
import org.scalacheck._, Arbitrary._
import enumeratum.scalacheck._

class DstReturnSchemaSpec extends FlatSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  def nonEmptyString: Gen[String] = arbitrary[String].filter(_.nonEmpty)

  def genMap: Gen[Map[Activity, Int]] = Gen.mapOf(
    (
      arbitrary[Activity],
      arbitrary[Int]
    ).tupled
  )

  def genGroupCo: Gen[GroupCompany] = (
    nonEmptyString,
    nonEmptyString
  ).mapN(GroupCompany.apply)

  def gencomap: Gen[Map[GroupCompany, Money]] = Gen.mapOf(
    (
      genGroupCo,
      arbitrary[Money]
    ).tupled
  )

  def genBankAccount: Gen[BankAccount] = {
    val genDomestic: Gen[DomesticBankAccount] = (
      nonEmptyString,
      nonEmptyString,
      arbitrary[Option[String]]
    ).mapN(DomesticBankAccount.apply)
    val genForeign: Gen[ForeignBankAccount] = nonEmptyString.map{ForeignBankAccount.apply}
    Gen.oneOf(genDomestic, genForeign)
  }

  def genRepayment: Gen[RepaymentDetails] = 
    (
      nonEmptyString,
      genBankAccount
    ).mapN(RepaymentDetails.apply)

  implicit def returnGen: Arbitrary[Return] = Arbitrary((
    genMap,
    arbitrary[Money],
    gencomap,
    arbitrary[Money],
    arbitrary[Money],
    Gen.option(genRepayment)
  ).mapN(Return.apply))

  def date(start: LocalDate, end: LocalDate): Gen[LocalDate] = 
    Gen.choose(start.toEpochDay, end.toEpochDay).map(LocalDate.ofEpochDay)

  implicit def periodArb: Arbitrary[Period] = Arbitrary((
    date(LocalDate.of(2010, 1, 1), LocalDate.of(2020, 1, 1)),
    date(LocalDate.of(2010, 1, 1), LocalDate.of(2020, 1, 1))    
  ).mapN(Period.apply))

  "A return API call" should "conform to the schema" in {
    forAll { (period: Period, ret: Return) => 
      val json = EeittInterface.returnRequestWriter("",period).writes(ret)
      SchemaChecker.EeittReturn.request.errorsIn(json) should be (None)
    }
  }

  "A registration API call" should "conform to the schema" in {
    val r = SubscriptionRequest(
      UnknownIdentification :: Nil,
      CustomerData("", "", Honorific.Mr, "Jennifer", "Alison", LocalDate.of(1989, 6, 10)), 
      NominatedCompany(ForeignAddress("Address Line 111", "Address Line 222", "Address Line 333", "Address Line 444", "Business Address Line 5", "", "DE"), "", "test@dstsystemtest.com"), 
      ContactDetails("Pascalle", "00447800399022", "pascalle@frenchparliament.com"),
      UltimateOwner("Group Holdings for DST", "GLOBAL ID 11223344556677", ForeignAddress("French Parliament Building", "La Chapelle", "Cheaveux", "Paris", "Correspondence Address Line 5", "", "FR")), 
      (LocalDate.now, LocalDate.now)
    )

    val json = EeittInterface.subscriptionRequestWriter.writes(r)
    import play.api.libs.json._
    println(Json.prettyPrint(json))
    SchemaChecker.EeittSubscribe.request.errorsIn(json) shouldBe (None)
  }
  
}
