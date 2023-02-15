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

package it.uk.gov.hmrc.digitalservicestax.controllers

import it.uk.gov.hmrc.digitalservicestax.helpers.ControllerBaseSpec
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalacheck.Arbitrary
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status, stubControllerComponents}
import uk.gov.hmrc.digitalservicestax.actions.LoggedInAction
import uk.gov.hmrc.digitalservicestax.controllers.RegistrationsController
import uk.gov.hmrc.digitalservicestax.data.BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.data.{DSTRegNumber, FormBundleNumber, InternalId, Registration}
import uk.gov.hmrc.digitalservicestax.services.MongoPersistence
import unit.uk.gov.hmrc.digitalservicestax.util.TestInstances._

import scala.concurrent.Future

class RegistrationsControllerSpec
    extends ControllerBaseSpec
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with ScalaFutures
    with IntegrationPatience {

  val mongoPersistence: MongoPersistence = app.injector.instanceOf[MongoPersistence]

  def controller(loginAction: LoggedInAction = loginReturn()): RegistrationsController =
    new RegistrationsController(
      authConnector = mockAuthConnector,
      cc = stubControllerComponents(),
      registrationConnector = mockRegistrationConnector,
      rosmConnector = mockRosmConnector,
      taxEnrolmentConnector = mockTaxEnrolmentsConnector,
      emailConnector = mockEmailConnector,
      persistence = mongoPersistence,
      taxEnrolmentService = mockTaxEnrolmentService,
      auditing = mockAuditing,
      loggedIn = loginAction
    )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockTaxEnrolmentService)
  }

  "RegistrationsController" must {

    "return 200 with registration data when get registration for the internalId is 'None' and for the groupId has data" in {

      val registration = Arbitrary.arbitrary[Registration].sample.value

      mockAuth()

      when(mockTaxEnrolmentService.getDSTRegistration(any())(any(), any())) thenReturn Future.successful(
        Some(registration)
      )

      val result: Future[Result] = controller().lookupRegistration().apply(FakeRequest())
      val resultStatus           = status(result)

      resultStatus mustBe 200
      contentAsString(
        result
      ) mustBe s"${Json.toJson(registration)}"

    }

    "return 200 with registration data when registrationNumber has data" in {
      val dstNumber    = Arbitrary.arbitrary[DSTRegNumber].sample.value
      val registration = Arbitrary.arbitrary[Registration].sample.value.copy(registrationNumber = Some(dstNumber))
      val internalId   = Arbitrary.arbitrary[InternalId].sample.value

      when(mockTaxEnrolmentService.getDSTRegistration(any())(any(), any())) thenReturn Future.successful(None)

      val chain = for {
        r <- mongoPersistence.registrations.update(internalId, registration)
      } yield r

      whenReady(chain) { _ =>
        val result: Future[Result] = controller(loginReturn(internalId)).lookupRegistration().apply(FakeRequest())
        val resultStatus           = status(result)

        resultStatus mustBe 200
        contentAsString(result) mustBe s"${Json.toJson(registration)}"
      }
    }

    "return 200 with registration data when registrationNumber is empty and FormBundleNumber is defined" in {
      val registration     = Arbitrary.arbitrary[Registration].sample.value.copy(registrationNumber = None)
      val internalId       = Arbitrary.arbitrary[InternalId].sample.value
      val formBundleNumber = Arbitrary.arbitrary[FormBundleNumber].sample.value

      when(mockTaxEnrolmentService.getDSTRegistration(any())(any(), any())) thenReturn Future.successful(None)

      val chain = for {
        r <- mongoPersistence.registrations.update(internalId, registration)
        m <- mongoPersistence.pendingCallbacks.update(formBundleNumber, internalId)
      } yield m

      whenReady(chain) { _ =>
        val result: Future[Result] = controller(loginReturn(internalId)).lookupRegistration().apply(FakeRequest())
        val resultStatus           = status(result)

        resultStatus mustBe 200
        contentAsString(result) mustBe s"${Json.toJson(registration)}"
      }

    }

    "return 404 when tax enrolments connector does not return DstRegNumber" in {
      mockAuth()
      when(mockTaxEnrolmentService.getDSTRegistration(any())(any(), any())) thenReturn Future.successful(
        None
      )
      val result: Future[Result] = controller().lookupRegistration().apply(FakeRequest())
      val resultStatus           = status(result)

      resultStatus mustBe 404
    }

  }
}
