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

package unit.uk.gov.hmrc.digitalservicestax.actions

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalacheck.Arbitrary.arbitrary
import org.scalactic.anyvals.PosInt
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.digitalservicestax.actions._
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.controllers.RegistrationsController
import uk.gov.hmrc.digitalservicestax.data.{DSTRegNumber, InternalId, NonEmptyString, Registration}
import uk.gov.hmrc.digitalservicestax.services.TaxEnrolmentService
import unit.uk.gov.hmrc.digitalservicestax.util.RetrievalOps._
import unit.uk.gov.hmrc.digitalservicestax.util.TestInstances._
import unit.uk.gov.hmrc.digitalservicestax.util.{FakeApplicationSetup, WiremockServer}

import scala.concurrent.Future
class ActionsSpec
    extends FakeApplicationSetup
    with WiremockServer
    with ScalaFutures
    with EitherValues
    with MockitoSugar
    with ScalaCheckDrivenPropertyChecks {

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAppConfig, mockTaxEnrolmentService)
  }

  val mockTaxEnrolmentService: TaxEnrolmentService = mock[TaxEnrolmentService]
  val mockAppConfig: AppConfig                     = mock[AppConfig]

  class Harness(authAction: LoggedInAction) {
    def onPageLoad(): Action[AnyContent] = authAction { _ =>
      Results.Ok
    }
  }

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(
    minSize = 1,
    minSuccessful = PosInt(1)
  )

  "Registered" should {
    "execute an action against a registered user using LoggedInRequest" in {
      val action = new Registered(mongoPersistence, mockAppConfig)

      val internal   = arbitrary[InternalId].sample.value
      val enrolments = arbitrary[Enrolments].sample.value
      val providerId = arbitrary[NonEmptyString].sample.value
      val reg        = arbitrary[Registration].sample.value

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId,
        Some("groupId"),
        FakeRequest()
      )

      val chain = for {
        _     <- mongoPersistence.registrations.update(internal, reg)
        block <- action.invokeBlock(
                   req,
                   { req: RegisteredRequest[_] =>
                     Future.successful(
                       Results.Ok(req.registration.registrationNumber.value)
                     )
                   }
                 )
      } yield block

      whenReady(chain) { resp =>
        resp.header.status mustEqual Status.OK
      }
    }

    "execute an action against a registered user using LoggedInRequest when taxEnrolmentService return registration" in {
      val action = new Registered(mongoPersistence, mockAppConfig)

      val internal   = arbitrary[InternalId].sample.value
      val enrolments = arbitrary[Enrolments].sample.value
      val providerId = arbitrary[NonEmptyString].sample.value
      val reg        = arbitrary[Registration].sample.value

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId,
        Some("groupId"),
        FakeRequest()
      )

      val chain = for {
        _     <- mongoPersistence.registrations.update(internal, reg)
        block <- action.invokeBlock(
                   req,
                   { req: RegisteredRequest[_] =>
                     Future.successful(
                       Results.Ok(req.registration.registrationNumber.value)
                     )
                   }
                 )
      } yield block

      whenReady(chain) { resp =>
        resp.header.status mustEqual Status.OK
      }
    }

    "return forbidden if there is no DST number on the registration" in {
      val action = new Registered(mongoPersistence, mockAppConfig)

      val internal    = arbitrary[InternalId].sample.value
      val enrolments  = arbitrary[Enrolments].sample.value
      val providerId  = arbitrary[NonEmptyString].sample.value
      val reg         = arbitrary[Registration].sample.value
      val regWithNoId = reg.copy(registrationNumber = None)

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId,
        Some("groupId"),
        FakeRequest()
      )

      val chain = for {
        _     <- mongoPersistence.registrations.update(internal, regWithNoId)
        block <- action.invokeBlock(
                   req,
                   { req: RegisteredRequest[_] =>
                     Future.successful(
                       Results.Ok(req.registration.registrationNumber.value)
                     )
                   }
                 )
      } yield block

      whenReady(chain) { resp =>
        resp.header.status mustEqual Status.FORBIDDEN
      }
    }

    "should not execute an action against a registered user using LoggedInRequest if the reg number is not defined" in {
      val action = new Registered(mongoPersistence, mockAppConfig)

      when(mockTaxEnrolmentService.getPendingDSTRegistration(any())(any(), any())).thenReturn(Future.successful(None))

      val internal   = arbitrary[InternalId].sample.value
      val enrolments = arbitrary[Enrolments].sample.value
      val providerId = arbitrary[NonEmptyString].sample.value

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId,
        Some("groupId"),
        FakeRequest()
      )

      whenReady(action.refine(req)) { res =>
        res.left.value.header.status mustBe Status.FORBIDDEN
      }
    }

    "should execute an action against a registered user using Registered or pending request" in {
      val action = new RegisteredOrPending(mongoPersistence, mockAppConfig)

      val internal   = arbitrary[InternalId].sample.value
      val enrolments = arbitrary[Enrolments].sample.value
      val providerId = arbitrary[NonEmptyString].sample.value
      val reg        = arbitrary[Registration].sample.value

      val loggedInReq = LoggedInRequest(
        internal,
        enrolments,
        providerId,
        Some("groupId"),
        FakeRequest()
      )

      val chain = for {
        _     <- mongoPersistence.registrations.update(internal, reg)
        block <- action.invokeBlock(
                   loggedInReq,
                   { req: RegisteredRequest[_] =>
                     Future.successful(
                       Results.Ok(req.registration.registrationNumber.value)
                     )
                   }
                 )
      } yield block

      whenReady(chain) { resp =>
        resp.header.status mustEqual Status.OK
      }
    }
  }

  "RegisteredOrPending.getRegistration" should {
    "execute an getRegistration details using LoggedInRequest with dstNewSolutionFeatureFlag false and registration details existing" in {

      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn false

      val internal   = arbitrary[InternalId].sample.value
      val enrolments = arbitrary[Enrolments].sample.value
      val providerId = arbitrary[NonEmptyString].sample.value
      val reg        = arbitrary[Registration].sample.value

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId,
        Some("groupId"),
        FakeRequest()
      )

      val chain = for {
        r <- mongoPersistence.registrations.update(internal, reg)
      } yield r

      whenReady(chain) { _ =>
        await(new RegisteredOrPending(mongoPersistence, mockAppConfig).getRegistration(req, mockAppConfig)) mustBe Some(
          reg
        )
      }
    }

    "execute an getRegistration using LoggedInRequest with dstNewSolutionFeatureFlag false and no registration details existing" in {

      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn false

      val internal   = arbitrary[InternalId].sample.value
      val enrolments = arbitrary[Enrolments].sample.value
      val providerId = arbitrary[NonEmptyString].sample.value

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId,
        Some("groupId"),
        FakeRequest()
      )

      await(new RegisteredOrPending(mongoPersistence, mockAppConfig).getRegistration(req, mockAppConfig)) mustEqual None
    }

    "execute an getRegistration details using LoggedInRequest with dstNewSolutionFeatureFlag true and DST enrolment exists" in {

      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn true

      val internal   = arbitrary[InternalId].sample.value
      val providerId = arbitrary[NonEmptyString].sample.value
      val reg        = arbitrary[Registration].sample.value
      val enrolments = Enrolments(
        Set(
          Enrolment("HMRC-DST-ORG", Seq(EnrolmentIdentifier("DSTRefNumber", reg.registrationNumber.get)), "Activated")
        )
      )

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId,
        Some("groupId"),
        FakeRequest()
      )

      val chain = for {
        r <- mongoPersistence.registrations.update(internal, reg)
      } yield r

      whenReady(chain) { _ =>
        await(
          new RegisteredOrPending(mongoPersistence, mockAppConfig).getRegistration(req, mockAppConfig)
        ) mustEqual Some(reg)
      }
    }

    "execute an getRegistration details using LoggedInRequest with dstNewSolutionFeatureFlag true and DST enrolment do not exists" in {

      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn true

      val internal   = arbitrary[InternalId].sample.value
      val enrolments = Enrolments(
        Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "123456789")), "Activated"))
      )
      val providerId = arbitrary[NonEmptyString].sample.value
      val reg        = arbitrary[Registration].sample.value

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId,
        Some("groupId"),
        FakeRequest()
      )

      val chain = for {
        r <- mongoPersistence.registrations.update(internal, reg)
      } yield r

      whenReady(chain) { _ =>
        await(
          new RegisteredOrPending(mongoPersistence, mockAppConfig).getRegistration(req, mockAppConfig)
        ) mustEqual None
      }
    }
  }

  "LoggedInAction" should {

    type AuthRetrievals = Enrolments ~ Option[String] ~ Option[Credentials] ~ Option[String]

    "return status FORBIDDEN when internalId is `None`" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]
      val retrieval: AuthRetrievals        =
        Enrolments(Set.empty) ~ None ~ Some(Credentials("providerId", "providerType")) ~ None
      when(mockAuthConnector.authorise[AuthRetrievals](any(), any())(any(), any())) thenReturn Future.successful(
        retrieval
      )

      val action = new LoggedInAction(stubMessagesControllerComponents(), appConfig, mockAuthConnector)

      val controller = new Harness(action)
      val result     = controller.onPageLoad()(FakeRequest("", ""))
      status(result) mustBe FORBIDDEN
    }

    "return status FORBIDDEN when credential is 'None'" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]
      val retrieval: AuthRetrievals        = Enrolments(Set.empty) ~ Some("Id") ~ None ~ None
      when(mockAuthConnector.authorise[AuthRetrievals](any(), any())(any(), any())) thenReturn Future.successful(
        retrieval
      )

      val action = new LoggedInAction(stubMessagesControllerComponents(), appConfig, mockAuthConnector)

      val controller = new Harness(action)
      val result     = controller.onPageLoad()(FakeRequest("", ""))
      status(result) mustBe FORBIDDEN
    }

    "return status FORBIDDEN when enrolment is empty" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]
      val retrieval: AuthRetrievals        =
        Enrolments(Set.empty) ~ Some("Id") ~ Some(Credentials("providerId", "providerType")) ~ None
      when(mockAuthConnector.authorise[AuthRetrievals](any(), any())(any(), any())) thenReturn Future.successful(
        retrieval
      )

      val action = new LoggedInAction(stubMessagesControllerComponents(), appConfig, mockAuthConnector)

      val controller = new Harness(action)
      val result     = controller.onPageLoad()(FakeRequest("", ""))
      status(result) mustBe FORBIDDEN
    }

    "return status OK for valid input" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]
      val enrolment: Enrolment             = Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "1234567")), "Activated")
      val retrieval: AuthRetrievals        =
        Enrolments(Set(enrolment)) ~ Some("Int-7e341-48319ddb53") ~ Some(
          Credentials("providerId", "providerType")
        ) ~ None
      when(mockAuthConnector.authorise[AuthRetrievals](any(), any())(any(), any())) thenReturn Future.successful(
        retrieval
      )

      val action = new LoggedInAction(stubMessagesControllerComponents(), appConfig, mockAuthConnector)

      val controller = new Harness(action)
      val result     = controller.onPageLoad()(FakeRequest("", ""))
      status(result) mustBe OK
    }

  }

}
