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

package unit.uk.gov.hmrc.digitalservicestax.data

import cats.implicits._
import cats.kernel.Monoid
import org.scalacheck.Gen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.digitalservicestax.data.{AccountNumber, Activity, Money, Percent, Period, Postcode}
import unit.uk.gov.hmrc.digitalservicestax.util.TestInstances.{arbMoney, periodArb}

class ValidatedTypeSpec extends AnyFlatSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  it should "fail to parse a validated tagged type using an of method" in {
    intercept[IllegalArgumentException] {
      Postcode("this is not a postcode")
    }
  }

  it should "correctly add 10 months with the first day of the month for period end days at the end of the month, " +
    "for all other cases add 9 months and one day to a period" in {
      forAll { period: Period =>
        if (period.end.getDayOfMonth == period.end.lengthOfMonth) {
          period.paymentDue shouldEqual period.end.plusMonths(10).withDayOfMonth(1)
        } else {
          period.paymentDue shouldEqual period.end.plusMonths(9).plusDays(1)
        }
      }
    }

  it should "correctly define the class name of a validated type" in {
    AccountNumber.className shouldEqual "AccountNumber$"
  }

  it should "store percentages as bytes and initialise percent monoids with float monoids" in {
    Monoid[Percent].empty shouldEqual Monoid[Float].empty
  }

  it should "add up percentages using monoidal syntax" in {

    val generator = for {
      p1 <- Gen.chooseNum(0, 100)
      p2  = 100 - p1
    } yield p1.toByte -> p2.toByte

    forAll(generator) { case (p1, p2) =>
      whenever(p1 >= 0 && p2 >= 0) {
        val addedPercent = Monoid.combineAll(Seq(Percent(p1), Percent(p2)))
        val addedBytes   = Monoid.combineAll(Seq(p1, p2))
        addedPercent shouldEqual Percent(addedBytes)
      }
    }
  }

  it should "encode an Activity as a URL" in {
    forAll(Gen.oneOf(Activity.values)) { a =>
      val expected = a.toString.replaceAll("(^[A-Z].*)([A-Z])", "$1-$2").toLowerCase
      Activity.toUrl(a) shouldEqual expected
    }
  }

  it should "fail to construct a type with a scale of more than 3" in {
    an[IllegalArgumentException] should be thrownBy Percent.apply(1.1234f)
  }

  it should "combine and empty Money correctly" in {
    forAll { (a: Money, b: Money) =>
      a.combine(b) - a     shouldEqual b
      a.combine(b) - b     shouldEqual a
      a.combine(b) - b - a shouldEqual Money.mon.empty
    }
  }
}
