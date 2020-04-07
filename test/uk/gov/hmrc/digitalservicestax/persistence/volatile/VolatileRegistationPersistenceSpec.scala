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

package uk.gov.hmrc.digitalservicestax.persistence.volatile

import org.scalactic.anyvals.PosInt
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.digitalservicestax.data.{InternalId, Registration}
import uk.gov.hmrc.digitalservicestax.services.FutureVolatilePersistence
import uk.gov.hmrc.digitalservicestax.util.FakeApplicationSpec
import uk.gov.hmrc.digitalservicestax.util.TestInstances._

import scala.concurrent.ExecutionContext.Implicits.global

class VolatileRegistationPersistenceSpec extends FakeApplicationSpec
  with ScalaFutures
  with BeforeAndAfterEach
  with ScalaCheckDrivenPropertyChecks {


  implicit override val generatorDrivenConfig =
    PropertyCheckConfiguration(minSize = 1, minSuccessful = PosInt(1))

  val volatile = new FutureVolatilePersistence(actorSystem = actorSystem) {}

  "it should retrieve a registration using .apply" in {
    forAll { (id: InternalId, reg: Registration) =>
      val chain = for {
        _ <- volatile.registrations.insert(id, reg)
        dbReg <- volatile.registrations(id)
      } yield dbReg

      whenReady(chain) { dbRes =>
        dbRes mustEqual reg
      }
    }
  }

  "it automatically generate a DST reg number if one is missing" in {
    forAll { (id: InternalId, reg: Registration) =>
      val updatedValue = reg.copy(registrationNumber = None)

      val chain = for {
        _ <- volatile.registrations.insert(id, updatedValue)
        dbReg <- volatile.registrations.get(id)
      } yield dbReg

      whenReady(chain) { _ =>
        reg.registrationNumber.isDefined mustBe true
      }
    }
  }

  "it should persist a registration object and retrieve it with .get" in {
    forAll { (id: InternalId, reg: Registration) =>
      val chain = for {
        _ <- volatile.registrations.insert(id, reg)
        dbReg <- volatile.registrations.get(id)
      } yield dbReg

      whenReady(chain) { dbRes =>
        dbRes.value mustEqual reg
      }
    }
  }

  "it should update a registration by userId" in {
    forAll { (id: InternalId, reg: Registration, updated: Registration) =>
      val chain = for {
        _ <- volatile.registrations.insert(id, reg)
        dbReg <- volatile.registrations.get(id)
        _ <- volatile.registrations.update(id, updated)
        postUpdate <- volatile.registrations.get(id)
      } yield dbReg -> postUpdate

      whenReady(chain) { case (dbRes, postUpdate) =>
        dbRes.value mustEqual reg
        postUpdate.value mustEqual updated
      }
    }
  }
}
