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
import uk.gov.hmrc.digitalservicestax.data.{FormBundleNumber, InternalId, SafeId}
import uk.gov.hmrc.digitalservicestax.services.FutureVolatilePersistence
import uk.gov.hmrc.digitalservicestax.util.FakeApplicationSpec
import uk.gov.hmrc.digitalservicestax.util.TestInstances._

import scala.concurrent.ExecutionContext.Implicits.global

class VolatilePendingEnrolmentsSpec extends FakeApplicationSpec
  with ScalaFutures
  with BeforeAndAfterEach
  with ScalaCheckDrivenPropertyChecks {

  implicit override val generatorDrivenConfig =
    PropertyCheckConfiguration(minSize = 1, minSuccessful = PosInt(1))

  val volatile = new FutureVolatilePersistence(actorSystem = actorSystem) {}

  "it fail to retrieve a non existing enrolment with a NoSuchElementException" in {
    forAll { (internalId: InternalId) =>
      whenReady(volatile.pendingEnrolments(internalId).failed) { ex =>
        ex mustBe a [NoSuchElementException]
        ex.getMessage mustBe s"user not found: $internalId"
      }
    }
  }

  "it should retrieve a pending enrolment id using the apply method" in {
    forAll { (internalId: InternalId, safeId: SafeId, formBundleNumber: FormBundleNumber) =>
      val chain = for {
        _ <- volatile.pendingEnrolments.insert(internalId, safeId, formBundleNumber)
        dbReg <- volatile.pendingEnrolments.apply(internalId)
      } yield dbReg

      whenReady(chain) { dbRes =>
        dbRes mustEqual safeId -> formBundleNumber
      }
    }
  }


  "it should retrieve a pending enrolment id using the get method" in {
    forAll { (internalId: InternalId, safeId: SafeId, formBundleNumber: FormBundleNumber) =>
      val chain = for {
        _ <- volatile.pendingEnrolments.insert(internalId, safeId, formBundleNumber)
        dbReg <- volatile.pendingEnrolments.get(internalId)
      } yield dbReg

      whenReady(chain) { dbRes =>
        dbRes mustBe defined
        dbRes.value mustEqual safeId -> formBundleNumber
      }
    }
  }


  "it should update an existing pending enrolment" in {
    forAll { (
      internalId: InternalId,
      safeId: SafeId,
      formBundleNumber: FormBundleNumber,
      newFormNumber: FormBundleNumber
    ) =>
      val chain = for {
        _ <- volatile.pendingEnrolments.insert(internalId, safeId, formBundleNumber)
        beforeUpdate <- volatile.pendingEnrolments.get(internalId)
        _ <- volatile.pendingEnrolments.update(internalId, safeId -> newFormNumber)
        afterUpdate <- volatile.pendingEnrolments.get(internalId)
      } yield beforeUpdate -> afterUpdate

      whenReady(chain) { case (beforeUpdate, afterUpdate) =>
        beforeUpdate mustBe defined
        beforeUpdate.value mustEqual safeId -> formBundleNumber

        afterUpdate mustBe defined
        afterUpdate.value mustEqual safeId -> newFormNumber
      }
    }
  }


  "it should delete a pending enrolment" in {
    forAll { (
      internalId: InternalId,
      safeId: SafeId,
      formBundleNumber: FormBundleNumber
    ) =>
      val chain = for {
        _ <- volatile.pendingEnrolments.insert(internalId, safeId, formBundleNumber)
        beforeUpdate <- volatile.pendingEnrolments.get(internalId)
        _ <- volatile.pendingEnrolments.delete(internalId)
        afterUpdate <- volatile.pendingEnrolments.get(internalId)
      } yield beforeUpdate -> afterUpdate

      whenReady(chain) { case (beforeUpdate, afterUpdate) =>
        beforeUpdate mustBe defined
        beforeUpdate.value mustEqual safeId -> formBundleNumber

        afterUpdate mustBe empty
      }
    }
  }

  "do nothing when consuming a non exising user id" in {
    forAll { internalId: InternalId =>
      whenReady(volatile.pendingEnrolments.consume(internalId)) { res =>
        res mustBe empty
      }
    }
  }


  "it should delete a pending enrolment with the consume method" in {
    forAll { (
      internalId: InternalId,
      safeId: SafeId,
      formBundleNumber: FormBundleNumber
    ) =>
      val chain = for {
        _ <- volatile.pendingEnrolments.insert(internalId, safeId, formBundleNumber)
        beforeUpdate <- volatile.pendingEnrolments.get(internalId)
        _ <- volatile.pendingEnrolments.consume(internalId)
        afterUpdate <- volatile.pendingEnrolments.get(internalId)
      } yield beforeUpdate -> afterUpdate

      whenReady(chain) { case (beforeUpdate, afterUpdate) =>
        beforeUpdate mustBe defined
        beforeUpdate.value mustEqual safeId -> formBundleNumber

        afterUpdate mustBe empty
      }
    }
  }
}
