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

import cats.kernel.Monoid
import com.outworkers.util.samplers._
import org.scalacheck.Gen
import org.scalatest.{FlatSpec, Matchers}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import cats.implicits._
import cats.syntax.monoid._

class ValidatedTypeTests extends FlatSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  it should "concatenate lines in a UKAddress value" in {

    val generator = for {
      nonEmptyLine1 <- Sample.generator[ShortString].map(ss => NonEmptyString(ss.value))
      line2 <- Gen.alphaNumStr
      line3 <- Gen.alphaNumStr
      line4 <- Gen.alphaNumStr
    } yield UkAddress(
      nonEmptyLine1,
      line2,
      line3,
      line4,
      Postcode("E1N 4WW")
    )

    forAll(generator) { ukAddress =>
      ukAddress.lines shouldEqual List(
        ukAddress.line1,
        ukAddress.line2,
        ukAddress.line3,
        ukAddress.line4,
        ukAddress.line5,
        ukAddress.postalCode,
        ukAddress.countryCode
      )
    }
  }

  it should "store percentages as bytes and initialise percent monoids with byte monoids" in {
    Monoid[Percent].empty shouldEqual Monoid[Byte].empty
  }

  it should "add up percentages using monoidal syntax" in {

    val generator = for {
      p1 <- Gen.chooseNum(0, 100)
      p2 = 100 - p1
    } yield p1.toByte -> p2.toByte

    forAll(generator) { case (p1, p2) =>
      whenever(p1 >=0 && p2 >= 0) {
        val addedPercent = Monoid.combineAll(Seq(Percent(p1), Percent(p2)))
        val addedBytes = Monoid.combineAll(Seq(p1, p2))
        addedPercent shouldEqual Percent(addedBytes)
      }
    }
  }
}
