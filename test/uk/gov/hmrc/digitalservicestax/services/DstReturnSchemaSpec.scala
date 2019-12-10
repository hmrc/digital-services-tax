/*
 * Copyright 2019 HM Revenue & Customs
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

class DstReturnSchemaSpec extends FlatSpec with Matchers {

  "A return API call" should "conform to the schema" in {
    val r = ReturnRequest(
      "BCD", //identification
      Period(LocalDate.of(2018,1,1), LocalDate.of(2019,12,31)), //period: (LocalDate, LocalDate),
      Map.empty,
      None,
      FinancialInformation(false, 1, 1),
      Nil,
      false
    )

    val json = EeittInterface.returnRequestWriter.writes(r)
    SchemaChecker.EeittReturn.request.errorsIn(json) shouldBe (None)
  }

  "A registration API call" should "conform to the schema" in {
    val r = SubscriptionRequest(
      orgName = "BlahdyCorp",
      OrganisationType.LimitedCompany,
      Customer(Honorific.Mr, "Joe", "Bloggs", LocalDate.of(1974, 1, 1)),
      ContactDetails(UkAddress("12 The Street", None, None, None, ""), "", None, None, ""),
      None
    )

    val json = EeittInterface.subscriptionRequestWriter.writes(r)
    SchemaChecker.EeittSubscribe.request.errorsIn(json) shouldBe (None)
  }
  
}
