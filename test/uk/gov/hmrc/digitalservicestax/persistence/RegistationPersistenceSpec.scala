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

import org.scalacheck.Arbitrary
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.digitalservicestax.data.{InternalId, Registration}
import uk.gov.hmrc.digitalservicestax.util.FakeApplicationSpec
import uk.gov.hmrc.digitalservicestax.util.TestInstances._

import scala.concurrent.ExecutionContext.Implicits.global

class RegistationPersistenceSpec extends FakeApplicationSpec
  with Matchers
  with ScalaFutures
  with MongoSpecSupport
  with ScalaCheckDrivenPropertyChecks {

  "it should persistent a registration object using the MongoConnector" in {

    implicit val internalIdArb: Arbitrary[InternalId] = Arbitrary(InternalId.gen)

    forAll { (id: InternalId, reg: Registration) =>
      val chain = for {
        _ <- mongoPersistence.registrations.insert(id, reg)
        dbReg <- mongoPersistence.registrations.get(id)
      } yield dbReg

      whenReady(chain) { dbRes =>
        dbRes shouldEqual reg
      }
    }

  }

}
