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

package uk.gov.hmrc.digitalservicestax.persistence

import org.scalactic.anyvals.PosInt
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.digitalservicestax.data.{FormBundleNumber, InternalId, Registration, SafeId}
import uk.gov.hmrc.digitalservicestax.util.FakeApplicationSpec
import uk.gov.hmrc.digitalservicestax.util.TestInstances._

import scala.concurrent.ExecutionContext.Implicits.global

class PendingEnrolmentsSpec extends FakeApplicationSpec
  with ScalaFutures
  with BeforeAndAfterEach
  with ScalaCheckDrivenPropertyChecks {

  implicit override val generatorDrivenConfig =
    PropertyCheckConfiguration(minSize = 1, minSuccessful = PosInt(1))

  "it fail to retrieve a non existing enrolment with a NoSuchElementException" in {
    forAll { (internalId: InternalId) =>
      val chain = for {
        dbReg <- mongoPersistence.pendingEnrolments.apply(internalId)
      } yield dbReg

      whenReady(chain.failed) { ex =>
        ex mustBe a [NoSuchElementException]
        ex.getMessage mustBe s"user not found: $internalId"
      }
    }
  }

  "it should retrieve a pending enrolment id using the apply method" in {
    forAll { (internalId: InternalId, safeId: SafeId, formBundleNumber: FormBundleNumber) =>
      val chain = for {
        _ <- mongoPersistence.pendingEnrolments.insert(internalId, safeId, formBundleNumber)
        dbReg <- mongoPersistence.pendingEnrolments.apply(internalId)
      } yield dbReg

      whenReady(chain) { dbRes =>
        dbRes mustEqual safeId -> formBundleNumber
      }
    }
  }


  "it should retrieve a pending enrolment id using the get method" in {
    forAll { (internalId: InternalId, safeId: SafeId, formBundleNumber: FormBundleNumber) =>
      val chain = for {
        _ <- mongoPersistence.pendingEnrolments.insert(internalId, safeId, formBundleNumber)
        dbReg <- mongoPersistence.pendingEnrolments.get(internalId)
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
        _ <- mongoPersistence.pendingEnrolments.insert(internalId, safeId, formBundleNumber)
        beforeUpdate <- mongoPersistence.pendingEnrolments.get(internalId)
        _ <- mongoPersistence.pendingEnrolments.update(internalId, safeId -> newFormNumber)
        afterUpdate <- mongoPersistence.pendingEnrolments.get(internalId)
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
        _ <- mongoPersistence.pendingEnrolments.insert(internalId, safeId, formBundleNumber)
        beforeUpdate <- mongoPersistence.pendingEnrolments.get(internalId)
        _ <- mongoPersistence.pendingEnrolments.delete(internalId)
        afterUpdate <- mongoPersistence.pendingEnrolments.get(internalId)
      } yield beforeUpdate -> afterUpdate

      whenReady(chain) { case (beforeUpdate, afterUpdate) =>
        beforeUpdate mustBe defined
        beforeUpdate.value mustEqual safeId -> formBundleNumber

        afterUpdate mustBe empty
      }
    }
  }

}
