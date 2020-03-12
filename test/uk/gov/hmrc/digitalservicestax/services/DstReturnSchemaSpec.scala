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

import org.scalatestplus.scalacheck._
import org.scalacheck._, Arbitrary.{arbBigDecimal => _, _}
import TestInstances._

class DstReturnSchemaSpec extends FlatSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  "A return API call" should "conform to the schema" in {
    forAll { (period: Period, ret: Return) =>
      val json = EeittInterface.returnRequestWriter("aoeu",period).writes(ret)
      SchemaChecker.EeittReturn.request.errorsIn(json) should be (None)
    }
  }

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
