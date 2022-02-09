/*
 * Copyright 2022 HM Revenue & Customs
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

package unit.uk.gov.hmrc.digitalservicestax.persistence

import org.scalactic.anyvals.PosInt
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.digitalservicestax.data.{InternalId, Registration}
import unit.uk.gov.hmrc.digitalservicestax.util.FakeApplicationSetup
import unit.uk.gov.hmrc.digitalservicestax.util.TestInstances._

class RegistationPersistenceSpec extends FakeApplicationSetup with ScalaFutures with BeforeAndAfterEach with ScalaCheckDrivenPropertyChecks {

  implicit override val generatorDrivenConfig =
    PropertyCheckConfiguration(minSize = 1, minSuccessful = PosInt(1))

  "it fail to retrieve a non existing registration with a NoSuchElementException using the apply method" in {
    forAll { id: InternalId =>
      whenReady(mongoPersistence.registrations(id).failed) { ex =>
        ex mustBe a [NoSuchElementException]
        ex.getMessage mustBe s"user not found: $id"
      }
    }
  }

  "it should safely return none for a non exsting registration id" in {
    forAll { id: InternalId =>
      whenReady(mongoPersistence.registrations.get(id)) { maybeReg =>
        maybeReg mustBe empty
      }
    }
  }

  "it should retrieve a registration using the apply object" in {
    forAll { (id: InternalId, reg: Registration) =>
      val chain = for {
        _ <- mongoPersistence.registrations.update(id, reg)
        dbReg <- mongoPersistence.registrations(id)
      } yield dbReg

      whenReady(chain) { dbRes =>
        dbRes mustEqual reg
      }
    }
  }

  "it should persist a registration object using the MongoConnector" in {
    forAll { (id: InternalId, reg: Registration) =>
      val chain = for {
        _ <- mongoPersistence.registrations.update(id, reg)
        dbReg <- mongoPersistence.registrations.get(id)
      } yield dbReg

      whenReady(chain) { dbRes =>
        dbRes.value mustEqual reg
      }
    }
  }

  "it should update a registration by userId" in {
    forAll { (id: InternalId, reg: Registration, updated: Registration) =>
      val chain = for {
        _ <- mongoPersistence.registrations.update(id, reg)
        dbReg <- mongoPersistence.registrations.get(id)
        _ <- mongoPersistence.registrations.update(id, updated)
        postUpdate <- mongoPersistence.registrations.get(id)
      } yield dbReg -> postUpdate

      whenReady(chain) { case (dbRes, postUpdate) =>
        dbRes.value mustEqual reg
        postUpdate.value mustEqual updated
      }
    }
  }
}
