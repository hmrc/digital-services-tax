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
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.digitalservicestax.actions.{LoggedInAction, RegisteredOrPending}
import uk.gov.hmrc.digitalservicestax.connectors.TaxEnrolmentsSubscription
import uk.gov.hmrc.digitalservicestax.controllers.RegistrationsController
import uk.gov.hmrc.digitalservicestax.data.BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.data.{CompanyRegWrapper, DSTRegNumber, FormBundleNumber, InternalId, Registration}
import uk.gov.hmrc.digitalservicestax.services.MongoPersistence
import unit.uk.gov.hmrc.digitalservicestax.util.TestInstances._

import scala.concurrent.Future

class RegistrationsControllerSpec
    extends ControllerBaseSpec
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with ScalaFutures
    with IntegrationPatience {

  val mongoPersistence: MongoPersistence           = app.injector.instanceOf[MongoPersistence]
  val mockRegisteredOrPending: RegisteredOrPending = mock[RegisteredOrPending]

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
      loggedIn = loginAction,
      appConfig = mockAppConfig,
      registrationOrPending = mockRegisteredOrPending
    )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockRegisteredOrPending, mockAppConfig, mockTaxEnrolmentService)
  }

  "RegistrationsController.lookupRegistration()" must {

    "return 200 with registration data when dstNewSolutionFeatureFlag is false and registrationNumber has data" in {
      val dstNumber    = Arbitrary.arbitrary[DSTRegNumber].sample.value
      val registration = Arbitrary.arbitrary[Registration].sample.value.copy(registrationNumber = Some(dstNumber))
      val internalId   = Arbitrary.arbitrary[InternalId].sample.value

      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn false

      when(mockRegisteredOrPending.getRegistration(any(), any())(any())) thenReturn Future.successful(
        Some(registration)
      )

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

    "return 200 with registration data when dstNewSolutionFeatureFlag is false and registrationNumber is empty and FormBundleNumber is defined" in {
      val registration     = Arbitrary.arbitrary[Registration].sample.value.copy(registrationNumber = None)
      val internalId       = Arbitrary.arbitrary[InternalId].sample.value
      val formBundleNumber = Arbitrary.arbitrary[FormBundleNumber].sample.value

      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn false

      when(mockRegisteredOrPending.getRegistration(any(), any())(any())) thenReturn Future.successful(
        Some(registration)
      )

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

    "return 200 with registration data when dstNewSolutionFeatureFlag is true and registration details with registrationNumber exists" in {
      val dstNumber        = Arbitrary.arbitrary[DSTRegNumber].sample.value
      val internalId       = Arbitrary.arbitrary[InternalId].sample.value
      val formBundleNumber = Arbitrary.arbitrary[FormBundleNumber].sample.value
      val registration     = Arbitrary.arbitrary[Registration].sample.value.copy(registrationNumber = Some(dstNumber))

      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn true

      when(mockRegisteredOrPending.getRegistration(any(), any())(any())) thenReturn Future.successful(
        Some(registration)
      )

      val chain = for {
        r <- mongoPersistence.registrations.update(internalId, registration)
        m <- mongoPersistence.pendingCallbacks.update(formBundleNumber, internalId)
      } yield m

      whenReady(chain) { _ =>
        val result: Future[Result] = controller(
          loginReturn(
            internalId,
            Enrolments(
              Set(Enrolment("HMRC-DST-ORG", Seq(EnrolmentIdentifier("DSTRefNumber", dstNumber)), "Activated"))
            )
          )
        ).lookupRegistration().apply(FakeRequest())
        val resultStatus           = status(result)

        resultStatus mustBe 200
        contentAsString(result) mustBe s"${Json.toJson(registration)}"
      }
    }

    "return 404 when dstNewSolutionFeatureFlag is false and no registration data in DB" in {

      mockAuth()

      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn false

      when(mockRegisteredOrPending.getRegistration(any(), any())(any())) thenReturn Future.successful(None)

      val result: Future[Result] = controller().lookupRegistration().apply(FakeRequest())
      val resultStatus           = status(result)

      resultStatus mustBe 404
    }

    "return 404 when dstNewSolutionFeatureFlag is true and tax enrolments connector does not return DstRegNumber" in {
      mockAuth()

      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn true

      when(mockRegisteredOrPending.getRegistration(any(), any())(any())) thenReturn Future.successful(None)

      val result: Future[Result] = controller().lookupRegistration().apply(FakeRequest())
      val resultStatus           = status(result)

      resultStatus mustBe 404
    }
  }

  "RegistrationsController.getTaxEnrolmentsPendingRegDetails()" must {

    "return 200 with registration data when dstNewSolutionFeatureFlag is true and DST enrolment is in pending state" in {
      val dstNumber  = Arbitrary.arbitrary[DSTRegNumber].sample.value
      val internalId = Arbitrary.arbitrary[InternalId].sample.value

      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn true

      when(mockTaxEnrolmentService.getPendingDSTRegistration(any())(any(), any())) thenReturn Future
        .successful(Ok)

      val result: Future[Result] =
        controller(loginReturn(internalId)).getTaxEnrolmentsPendingRegDetails().apply(FakeRequest())
      val resultStatus           = status(result)

      resultStatus mustBe 200
    }

    "return 404 with registration data when dstNewSolutionFeatureFlag is true and DST enrolment is in non-pending state" in {
      val internalId = Arbitrary.arbitrary[InternalId].sample.value

      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn true

      when(mockTaxEnrolmentService.getPendingDSTRegistration(any())(any(), any())) thenReturn Future.successful(
        NotFound
      )

      val result: Future[Result] =
        controller(loginReturn(internalId)).getTaxEnrolmentsPendingRegDetails().apply(FakeRequest())
      val resultStatus           = status(result)

      resultStatus mustBe 404
    }

    "return 404 with None when dstNewSolutionFeatureFlag is false" in {
      val internalId = Arbitrary.arbitrary[InternalId].sample.value

      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn false

      val result: Future[Result] =
        controller(loginReturn(internalId)).getTaxEnrolmentsPendingRegDetails().apply(FakeRequest())
      val resultStatus           = status(result)

      resultStatus mustBe 404
    }
  }
}
