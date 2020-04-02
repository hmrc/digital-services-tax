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

package uk.gov.hmrc.digitalservicestax.actions

import org.scalactic.anyvals.PosInt
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.{ContentTypes, Status}
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.digitalservicestax.data.{InternalId, NonEmptyString, Registration}
import uk.gov.hmrc.digitalservicestax.util.FakeApplicationSpec
import uk.gov.hmrc.digitalservicestax.util.TestInstances._
import play.api.mvc.Results
import play.api.mvc.Results.Forbidden

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ActionsTest extends FakeApplicationSpec
  with ScalaFutures
  with BeforeAndAfterEach
  with EitherValues
  with ScalaCheckDrivenPropertyChecks {

  implicit override val generatorDrivenConfig = PropertyCheckConfiguration(
    minSize = 1,
    minSuccessful = PosInt(1)
  )

  "should execute an action against a registered user using LoggedInRequest" in {
    val action = new Registered(mongoPersistence)

    forAll { (internal: InternalId, enrolments: Enrolments, providerId: NonEmptyString, reg: Registration) =>

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId,
        FakeRequest()
      )

      val chain = for {
        _ <- mongoPersistence.registrations.insert(internal, reg)
        block <- action.invokeBlock(req, { req: RegisteredRequest[_] =>
          Future.successful(
            Results.Ok(req.registration.registrationNumber.value)
          )
        })
      } yield block

      whenReady(chain) { resp =>
        resp.header.status mustEqual Status.OK
      }
    }
  }

  "should not execute an action against a registered user using LoggedInRequest if the reg number is not defined" in {
    val action = new Registered(mongoPersistence)

    forAll { (internal: InternalId, enrolments: Enrolments, providerId: NonEmptyString) =>

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId,
        FakeRequest()
      )

      whenReady(action.refine(req)) { res =>
        res.left.value.header.status mustBe Status.FORBIDDEN
      }
    }
  }

  "should execute an action against a registered user using Registered request" in {
    val action = new RegisteredOrPending(mongoPersistence)

    forAll { (internal: InternalId, enrolments: Enrolments, providerId: NonEmptyString, reg: Registration) =>
      val loggedInReq = LoggedInRequest(
        internal,
        enrolments,
        providerId,
        FakeRequest()
      )

      val chain = for {
        _ <- mongoPersistence.registrations.insert(internal, reg)
        block <- action.invokeBlock(loggedInReq, { req: RegisteredRequest[_] =>
          Future.successful(
            Results.Ok(req.registration.registrationNumber.value)
          )
        })
      } yield block

      whenReady(chain) { resp =>
        resp.header.status mustEqual Status.OK
      }
    }
  }

}