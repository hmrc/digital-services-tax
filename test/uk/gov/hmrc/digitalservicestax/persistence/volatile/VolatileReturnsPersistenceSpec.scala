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

package uk.gov.hmrc.digitalservicestax.persistence.volatile

import org.scalactic.anyvals.PosInt
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.digitalservicestax.data.{Period, Registration, Return}
import uk.gov.hmrc.digitalservicestax.services.FutureVolatilePersistence
import uk.gov.hmrc.digitalservicestax.util.FakeApplicationSpec
import uk.gov.hmrc.digitalservicestax.util.TestInstances._

import scala.concurrent.ExecutionContext.Implicits.global

class VolatileReturnsPersistenceSpec extends FakeApplicationSpec
  with ScalaFutures
  with ScalaCheckDrivenPropertyChecks {

  implicit override val generatorDrivenConfig =
    PropertyCheckConfiguration(minSize = 1, minSuccessful = PosInt(1))

  val period = Period.Key.of("0220").value

  val volatile = new FutureVolatilePersistence(actorSystem = actorSystem) {}

  "it should persist a return object using the apply method" in {
    forAll { (reg: Registration, ret: Return) =>
      val chain = for {
        _ <- volatile.returns.insert(reg, period, ret)
        dbReg <- volatile.returns(reg, period)
      } yield dbReg

      whenReady(chain) { dbRes =>
        dbRes mustEqual ret
      }
    }
  }

  "it should persist a return object" in {
    forAll { (reg: Registration, ret: Return) =>
      val chain = for {
        _ <- volatile.returns.insert(reg, period, ret)
        dbReg <- volatile.returns.get(reg, period)
      } yield dbReg

      whenReady(chain) { dbRes =>
        dbRes.value mustEqual ret
      }
    }
  }

  "it should update a return by registration and period" in {
    forAll { (reg: Registration, ret: Return, updatedReturn: Return) =>
      val chain = for {
        _ <- volatile.returns.insert(reg, period, ret)
        dbReg <- volatile.returns.get(reg, period)
        _ <- volatile.returns.update(reg, period, updatedReturn)
        postUpdate <- volatile.returns.get(reg, period)
      } yield dbReg -> postUpdate

      whenReady(chain) { case (dbRes, postUpdate) =>
        dbRes.value mustEqual ret
        postUpdate.value mustEqual updatedReturn
      }
    }
  }
}
