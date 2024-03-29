/*
 * Copyright 2023 HM Revenue & Customs
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

package unit.uk.gov.hmrc.digitalservicestax.services

import org.scalacheck.Arbitrary.{arbBigDecimal => _}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck._
import uk.gov.hmrc.digitalservicestax.data._
import uk.gov.hmrc.digitalservicestax.services.EeittInterface
import unit.uk.gov.hmrc.digitalservicestax.util.TestInstances._

class DstReturnSchemaSpec extends AnyFlatSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  "A return API call"       should "conform to the schema" in {
    forAll { (period: Period, ret: Return) =>
      val json = EeittInterface.returnRequestWriter("aoeu", period).writes(ret)
      SchemaChecker.EeittReturn.request.errorsIn(json) should be(None)

      // testing that the receivedAt timestamp seconds is in millis
      val receivedAtSeconds = json("receivedAt").as[String].split('.').last
      receivedAtSeconds.size should be(4)
    }
  }

  "A registration API call" should "conform to the schema" in {
    forAll { (subRequest: Registration) =>
      val json = EeittInterface.registrationWriter.writes(subRequest)
      SchemaChecker.EeittSubscribe.request.errorsIn(json) shouldBe None
    }
  }

}
