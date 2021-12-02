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

package unit.uk.gov.hmrc.digitalservicestax.persistence.volatile

import org.scalactic.anyvals.PosInt
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.digitalservicestax.data.{FormBundleNumber, InternalId, Registration}
import unit.uk.gov.hmrc.digitalservicestax.services.FutureVolatilePersistence
import unit.uk.gov.hmrc.digitalservicestax.util.FakeApplicationSetup
import unit.uk.gov.hmrc.digitalservicestax.util.TestInstances._

class VolatilePendingCallbacksSpec extends FakeApplicationSetup with ScalaFutures with BeforeAndAfterEach with ScalaCheckDrivenPropertyChecks {

  implicit override val generatorDrivenConfig =
    PropertyCheckConfiguration(minSize = 1, minSuccessful = PosInt(1))

  val volatile = new FutureVolatilePersistence() {}

  "it should retrieve a pending callback id using the apply object" in {
    forAll { (formNo: FormBundleNumber, id: InternalId) =>
      val chain = for {
        _ <- volatile.pendingCallbacks.update(formNo, id)
        dbReg <- volatile.pendingCallbacks(formNo)
      } yield dbReg

      whenReady(chain) { dbRes =>
        dbRes mustEqual id
      }
    }
  }

  "it should retrieve a pending callback id using the get method" in {
    forAll { (formNo: FormBundleNumber, id: InternalId) =>
      val chain = for {
        _ <- volatile.pendingCallbacks.update(formNo, id)
        dbReg <- volatile.pendingCallbacks.get(formNo)
      } yield dbReg

      whenReady(chain) { dbRes =>
        dbRes.value mustEqual id
      }
    }
  }

  "it should confirm a registraion using the pending callbacks process" in {
    forAll { (formNo: FormBundleNumber, id: InternalId, reg: Registration) =>
      val chain = for {
        _ <- volatile.registrations.update(id, reg)
        _ <- volatile.pendingCallbacks.update(formNo, id)
        _ <- volatile.pendingCallbacks.process(formNo, reg.registrationNumber.value)
        formBundle <- volatile.pendingCallbacks.get(formNo)
      } yield formBundle

      whenReady(chain) { dbRes =>
        dbRes mustBe empty
      }
    }
  }

  "it should update a pending callback ID by form number" in {
    forAll { (formNo: FormBundleNumber, id: InternalId, newId: InternalId) =>
      val chain = for {
        _ <- volatile.pendingCallbacks.update(formNo, id)
        dbReg <- volatile.pendingCallbacks.get(formNo)
        _ <- volatile.pendingCallbacks.update(formNo, newId)
        postUpdate <- volatile.pendingCallbacks.get(formNo)
      } yield dbReg -> postUpdate


      whenReady(chain) { case (dbRes, postUpdate) =>
        dbRes.value mustEqual id
        postUpdate.value mustEqual newId
      }
    }
  }

  "it should delete a pending callback by its form number" in {
    forAll { (formNo: FormBundleNumber, id: InternalId, newId: InternalId) =>
      val chain = for {
        _ <- volatile.pendingCallbacks.update(formNo, id)
        dbReg <- volatile.pendingCallbacks.get(formNo)
        _ <- volatile.pendingCallbacks.delete(formNo)
        postUpdate <- volatile.pendingCallbacks.get(formNo)
      } yield dbReg -> postUpdate


      whenReady(chain) { case (dbRes, postUpdate) =>
        dbRes.value mustEqual id
        postUpdate mustBe empty
      }
    }
  }
}
