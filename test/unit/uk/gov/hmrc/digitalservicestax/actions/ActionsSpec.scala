/*
 * Copyright 2026 HM Revenue & Customs
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

import it.uk.gov.hmrc.digitalservicestax.util.TestInstances
import org.mockito.ArgumentMatchers.{any, same}
import org.mockito.Mockito.{reset, when}
import org.scalacheck.Arbitrary.arbitrary
import org.scalactic.anyvals.PosInt
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.Status
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.digitalservicestax.actions._
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors.{EnrolmentStoreProxyConnector, TaxEnrolmentConnector}
import uk.gov.hmrc.digitalservicestax.data.{FormBundleNumber, InternalId, NonEmptyString, Registration, UTR}
import uk.gov.hmrc.digitalservicestax.services.{GetDstNumberFromEisService, TaxEnrolmentService}
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

  val mockTaxEnrolmentService: TaxEnrolmentService               = mock[TaxEnrolmentService]
  val mockTaxEnrolmentConnector: TaxEnrolmentConnector           = mock[TaxEnrolmentConnector]
  val mockAppConfig: AppConfig                                   = mock[AppConfig]
  val mockEspConnector: EnrolmentStoreProxyConnector             = mock[EnrolmentStoreProxyConnector]
  val mockGetDstNumberFromEisService: GetDstNumberFromEisService = mock[GetDstNumberFromEisService]

  class Harness(authAction: LoggedInAction) {
    def onPageLoad(): Action[AnyContent] = authAction { _ =>
      Results.Ok
    }
  }

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(
    minSize = 1,
    minSuccessful = PosInt(1)
  )

  type AuthRetrievals = Enrolments ~ Option[String] ~ Option[Credentials] ~ Option[String]

  "Registered" should {
    "execute an action against a registered user using LoggedInRequest" in {
      val action = new Registered(
        mongoPersistence,
        mockAppConfig,
        mockEspConnector,
        mockGetDstNumberFromEisService,
        mockTaxEnrolmentConnector
      )

      val internal   = TestInstances.arbInternalId.arbitrary.sample.value
      val enrolments = arbitrary[Enrolments].sample.value
      val providerId = arbitrary[NonEmptyString].sample.value
      val reg        = arbitrary[Registration].sample.value

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId.value,
        Some("groupId"),
        FakeRequest()
      )

      val chain = for {
        _     <- mongoPersistence.registrations.update(internal, reg)
        block <- action.invokeBlock(
                   req,
                   { (req: RegisteredRequest[_]) =>
                     Future.successful(
                       Results.Ok(req.registration.registrationNumber.value.value)
                     )
                   }
                 )
      } yield block

      whenReady(chain) { resp =>
        resp.header.status mustEqual Status.OK
      }
    }

    "execute an action against a registered user using LoggedInRequest when taxEnrolmentService return registration" in {
      val action = new Registered(
        mongoPersistence,
        mockAppConfig,
        mockEspConnector,
        mockGetDstNumberFromEisService,
        mockTaxEnrolmentConnector
      )

      val internal   = TestInstances.arbInternalId.arbitrary.sample.value
      val enrolments = arbitrary[Enrolments].sample.value
      val providerId = arbitrary[NonEmptyString].sample.value
      val reg        = arbitrary[Registration].sample.value

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId.value,
        Some("groupId"),
        FakeRequest()
      )

      val chain = for {
        _     <- mongoPersistence.registrations.update(internal, reg)
        block <- action.invokeBlock(
                   req,
                   { (req: RegisteredRequest[_]) =>
                     Future.successful(
                       Results.Ok(req.registration.registrationNumber.value.value)
                     )
                   }
                 )
      } yield block

      whenReady(chain) { resp =>
        resp.header.status mustEqual Status.OK
      }
    }

    "return forbidden if there is no DST number on the registration" in {
      val action = new Registered(
        mongoPersistence,
        mockAppConfig,
        mockEspConnector,
        mockGetDstNumberFromEisService,
        mockTaxEnrolmentConnector
      )

      val internal    = TestInstances.arbInternalId.arbitrary.sample.value
      val enrolments  = arbitrary[Enrolments].sample.value
      val providerId  = arbitrary[NonEmptyString].sample.value
      val reg         = arbitrary[Registration].sample.value
      val regWithNoId = reg.copy(registrationNumber = None)

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId.value,
        Some("groupId"),
        FakeRequest()
      )

      val chain = for {
        _     <- mongoPersistence.registrations.update(internal, regWithNoId)
        block <- action.invokeBlock(
                   req,
                   { (req: RegisteredRequest[_]) =>
                     Future.successful(
                       Results.Ok(req.registration.registrationNumber.value.value)
                     )
                   }
                 )
      } yield block

      whenReady(chain) { resp =>
        resp.header.status mustEqual Status.FORBIDDEN
      }
    }

    "should not execute an action against a registered user using LoggedInRequest if the reg number is not defined" in {
      val action = new Registered(
        mongoPersistence,
        mockAppConfig,
        mockEspConnector,
        mockGetDstNumberFromEisService,
        mockTaxEnrolmentConnector
      )

      val internal   = TestInstances.arbInternalId.arbitrary.sample.value
      val enrolments = arbitrary[Enrolments].sample.value
      val providerId = arbitrary[NonEmptyString].sample.value

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId.value,
        Some("groupId"),
        FakeRequest()
      )

      whenReady(action.refine(req)) { res =>
        res.left.value.header.status mustBe Status.FORBIDDEN
      }
    }

    "should execute an action against a registered user using Registered or pending request" in {
      val action = new RegisteredOrPending(
        mongoPersistence,
        mockAppConfig,
        mockEspConnector,
        mockGetDstNumberFromEisService,
        mockTaxEnrolmentConnector
      )

      val internal   = TestInstances.arbInternalId.arbitrary.sample.value
      val enrolments = arbitrary[Enrolments].sample.value
      val providerId = arbitrary[NonEmptyString].sample.value
      val reg        = arbitrary[Registration].sample.value

      val loggedInReq = LoggedInRequest(
        internal,
        enrolments,
        providerId.value,
        Some("groupId"),
        FakeRequest()
      )

      val chain = for {
        _     <- mongoPersistence.registrations.update(internal, reg)
        block <- action.invokeBlock(
                   loggedInReq,
                   { (req: RegisteredRequest[_]) =>
                     Future.successful(
                       Results.Ok(req.registration.registrationNumber.value.value)
                     )
                   }
                 )
      } yield block

      whenReady(chain) { resp =>
        resp.header.status mustEqual Status.OK
      }
    }
  }

  "RegisteredOrPending.activateDstEnrolmentFromConfig" should {
    "dstNewSolutionFeatureFlag is true and DST enrolment does not exists in EACD" when {
      "execute an activateDstEnrolmentFromConfig details when groupId matches the one in config" when {
        "return registration details with Dst reference number when record exists with Fb number" in {

          when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn true
          when(mockAppConfig.dstRefAndGroupIdActivationFeatureFlag) thenReturn true

          val internal = TestInstances.arbInternalId.arbitrary.sample.value
          val reg      = arbitrary[Registration].sample.value
          val fbNumber = arbitrary[FormBundleNumber].sample.value

          when(mockAppConfig.fbNumberForActivation) thenReturn FormBundleNumber(fbNumber.value)
          when(mockAppConfig.groupIdForActivation) thenReturn "groupId"
          when(mockAppConfig.dstRefNumberForActivation) thenReturn reg.registrationNumber.get

          when(
            mockTaxEnrolmentConnector.isAllocateDstGroupEnrolmentSuccess(any(), any())(any(), any())
          ) thenReturn Future
            .successful(true)
          when(mockEspConnector.getDstRefFromGroupAssignedEnrolment(any())(any(), any())) thenReturn Future.successful(
            Some(reg.registrationNumber.value)
          )

          val chain = for {
            m <- mongoPersistence.pendingCallbacks.update(fbNumber, internal)
            r <- mongoPersistence.registrations.update(internal, reg.copy(registrationNumber = None))
          } yield r

          whenReady(chain) { _ =>
            val registration = await(
              new RegisteredOrPending(
                mongoPersistence,
                mockAppConfig,
                mockEspConnector,
                mockGetDstNumberFromEisService,
                mockTaxEnrolmentConnector
              )
                .activateDstEnrolmentFromConfig("groupId", mockAppConfig)
            )
            registration mustEqual Some(reg)
            registration.get.registrationNumber mustEqual reg.registrationNumber
          }
        }
        "return None when record does not exists with Fb number" in {

          when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn true
          when(mockAppConfig.dstRefAndGroupIdActivationFeatureFlag) thenReturn true

          val internal = TestInstances.arbInternalId.arbitrary.sample.value
          val reg      = arbitrary[Registration].sample.value
          val fbNumber = arbitrary[FormBundleNumber].sample.value

          when(mockAppConfig.fbNumberForActivation) thenReturn FormBundleNumber(fbNumber.value)
          when(mockAppConfig.groupIdForActivation) thenReturn "groupId"
          when(mockAppConfig.dstRefNumberForActivation) thenReturn reg.registrationNumber.get

          when(
            mockTaxEnrolmentConnector.isAllocateDstGroupEnrolmentSuccess(any(), any())(any(), any())
          ) thenReturn Future
            .successful(true)
          when(mockEspConnector.getDstRefFromGroupAssignedEnrolment(any())(any(), any())) thenReturn Future.successful(
            Some(reg.registrationNumber.value)
          )

          val chain = for {
            r <- mongoPersistence.registrations.update(internal, reg)
          } yield r

          whenReady(chain) { _ =>
            val registration = await(
              new RegisteredOrPending(
                mongoPersistence,
                mockAppConfig,
                mockEspConnector,
                mockGetDstNumberFromEisService,
                mockTaxEnrolmentConnector
              )
                .activateDstEnrolmentFromConfig("groupId", mockAppConfig)
            )
            registration mustEqual None
          }
        }
        "return registration details with no dst ref when record exists with Fb number but Tax enrolment call fails" in {

          when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn true
          when(mockAppConfig.dstRefAndGroupIdActivationFeatureFlag) thenReturn true

          val internal = TestInstances.arbInternalId.arbitrary.sample.value
          val reg      = arbitrary[Registration].sample.value.copy(registrationNumber = None)
          val fbNumber = arbitrary[FormBundleNumber].sample.value

          when(mockAppConfig.fbNumberForActivation) thenReturn FormBundleNumber(fbNumber.value)
          when(mockAppConfig.groupIdForActivation) thenReturn "groupId"
          when(mockAppConfig.dstRefNumberForActivation) thenReturn "KODST8387428400"

          when(
            mockTaxEnrolmentConnector.isAllocateDstGroupEnrolmentSuccess(any(), any())(any(), any())
          ) thenReturn Future
            .successful(false)

          val chain = for {
            m <- mongoPersistence.pendingCallbacks.update(fbNumber, internal)
            r <- mongoPersistence.registrations.update(internal, reg)
          } yield r

          whenReady(chain) { _ =>
            val registration = await(
              new RegisteredOrPending(
                mongoPersistence,
                mockAppConfig,
                mockEspConnector,
                mockGetDstNumberFromEisService,
                mockTaxEnrolmentConnector
              )
                .activateDstEnrolmentFromConfig("groupId", mockAppConfig)
            )
            registration mustEqual Some(reg)
            registration.get.registrationNumber mustEqual None
          }
        }
        "return registration details with no Dst reference number when tax enrolment is successful but no ES record" in {

          when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn true
          when(mockAppConfig.dstRefAndGroupIdActivationFeatureFlag) thenReturn true

          val internal = TestInstances.arbInternalId.arbitrary.sample.value
          val reg      = arbitrary[Registration].sample.value.copy(registrationNumber = None)
          val fbNumber = arbitrary[FormBundleNumber].sample.value

          when(mockAppConfig.fbNumberForActivation) thenReturn FormBundleNumber(fbNumber.value)
          when(mockAppConfig.groupIdForActivation) thenReturn "groupId"
          when(mockAppConfig.dstRefNumberForActivation) thenReturn "KODST8387420000"

          when(
            mockTaxEnrolmentConnector.isAllocateDstGroupEnrolmentSuccess(any(), any())(any(), any())
          ) thenReturn Future
            .successful(true)
          when(mockEspConnector.getDstRefFromGroupAssignedEnrolment(any())(any(), any())) thenReturn Future.successful(
            None
          )

          val chain = for {
            m <- mongoPersistence.pendingCallbacks.update(fbNumber, internal)
            r <- mongoPersistence.registrations.update(internal, reg)
          } yield r

          whenReady(chain) { _ =>
            val registration = await(
              new RegisteredOrPending(
                mongoPersistence,
                mockAppConfig,
                mockEspConnector,
                mockGetDstNumberFromEisService,
                mockTaxEnrolmentConnector
              )
                .activateDstEnrolmentFromConfig("groupId", mockAppConfig)
            )
            registration mustEqual Some(reg)
            registration.get.registrationNumber mustEqual None
          }
        }
      }
    }
  }
  "RegisteredOrPending.getRegistration"                should {
    "execute an getRegistration details using LoggedInRequest with dstNewSolutionFeatureFlag false and registration details existing" in {

      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn false

      val internal   = TestInstances.arbInternalId.arbitrary.sample.value
      val enrolments = arbitrary[Enrolments].sample.value
      val providerId = arbitrary[NonEmptyString].sample.value
      val reg        = arbitrary[Registration].sample.value

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId.value,
        Some("groupId"),
        FakeRequest()
      )

      val chain = for {
        r <- mongoPersistence.registrations.update(internal, reg)
      } yield r

      whenReady(chain) { _ =>
        await(
          new RegisteredOrPending(
            mongoPersistence,
            mockAppConfig,
            mockEspConnector,
            mockGetDstNumberFromEisService,
            mockTaxEnrolmentConnector
          )
            .getRegistration(req, mockAppConfig)
        ) mustBe Some(
          reg
        )
      }
    }

    "execute an getRegistration using LoggedInRequest with dstNewSolutionFeatureFlag false and no registration details existing" in {

      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn false

      val internal   = TestInstances.arbInternalId.arbitrary.sample.value
      val enrolments = arbitrary[Enrolments].sample.value
      val providerId = arbitrary[NonEmptyString].sample.value

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId.value,
        Some("groupId"),
        FakeRequest()
      )

      await(
        new RegisteredOrPending(
          mongoPersistence,
          mockAppConfig,
          mockEspConnector,
          mockGetDstNumberFromEisService,
          mockTaxEnrolmentConnector
        )
          .getRegistration(req, mockAppConfig)
      ) mustEqual None
    }

    "execute an getRegistration details using LoggedInRequest with dstNewSolutionFeatureFlag true and DST enrolment exists in EACD" in {

      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn true

      val internal   = TestInstances.arbInternalId.arbitrary.sample.value
      val providerId = arbitrary[NonEmptyString].sample.value
      val reg        = arbitrary[Registration].sample.value
      val enrolments = Enrolments(
        Set(
          Enrolment("HMRC-DST-ORG", Seq(EnrolmentIdentifier("DSTRefNumber", reg.registrationNumber.value.value)), "Activated")
        )
      )

      when(mockEspConnector.getDstRefFromGroupAssignedEnrolment(any())(any(), any())) thenReturn Future.successful(
        Some(reg.registrationNumber.value)
      )

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId.value,
        Some("groupId"),
        FakeRequest()
      )

      val chain = for {
        r <- mongoPersistence.registrations.update(internal, reg)
      } yield r

      whenReady(chain) { _ =>
        await(
          new RegisteredOrPending(
            mongoPersistence,
            mockAppConfig,
            mockEspConnector,
            mockGetDstNumberFromEisService,
            mockTaxEnrolmentConnector
          )
            .getRegistration(req, mockAppConfig)
        ) mustEqual Some(reg)
      }
    }

    "execute an getRegistration details using LoggedInRequest with dstNewSolutionFeatureFlag true and DST enrolment exists in EIS & activation is successful" in {

      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn true

      val internal   = TestInstances.arbInternalId.arbitrary.sample.value
      val utr        = arbitrary[UTR].sample.value
      val providerId = arbitrary[NonEmptyString].sample.value
      val reg        = arbitrary[Registration].sample.value
      val enrolments = Enrolments(
        Set(
          Enrolment("HMRC-DST-ORG", Seq(EnrolmentIdentifier("DSTRefNumber", reg.registrationNumber.value.value)), "Activated"),
          Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", utr.value)), "Activated")
        )
      )

      when(mockEspConnector.getDstRefFromGroupAssignedEnrolment(any())(any(), any())) thenReturn Future.successful(None)
      when(
        mockGetDstNumberFromEisService.getDstNumberAndActivateEnrolment(same(utr), same("groupId"))(any(), any())
      ) thenReturn Future.successful(Some(reg))

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId.value,
        Some("groupId"),
        FakeRequest()
      )

      val chain = for {
        r <- mongoPersistence.registrations.update(internal, reg)
      } yield r

      whenReady(chain) { _ =>
        await(
          new RegisteredOrPending(
            mongoPersistence,
            mockAppConfig,
            mockEspConnector,
            mockGetDstNumberFromEisService,
            mockTaxEnrolmentConnector
          )
            .getRegistration(req, mockAppConfig)
        ) mustEqual Some(reg)
      }
    }

    "execute an getRegistration details using LoggedInRequest with dstNewSolutionFeatureFlag true and DST enrolment do not exists in EACD & EIS" in {

      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn true

      val internal   = TestInstances.arbInternalId.arbitrary.sample.value
      val enrolments = Enrolments(
        Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "1234567890")), "Activated"))
      )
      val providerId = arbitrary[NonEmptyString].sample.value
      val reg        = arbitrary[Registration].sample.value

      when(mockEspConnector.getDstRefFromGroupAssignedEnrolment(any())(any(), any())) thenReturn Future.successful(
        None
      )
      when(
        mockGetDstNumberFromEisService
          .getDstNumberAndActivateEnrolment(same(UTR("1234567890")), same("groupId"))(any(), any())
      ) thenReturn Future.successful(
        None
      )

      val req = LoggedInRequest(
        internal,
        enrolments,
        providerId.value,
        Some("groupId"),
        FakeRequest()
      )

      val chain = for {
        r <- mongoPersistence.registrations.update(internal, reg)
      } yield r

      whenReady(chain) { _ =>
        await(
          new RegisteredOrPending(
            mongoPersistence,
            mockAppConfig,
            mockEspConnector,
            mockGetDstNumberFromEisService,
            mockTaxEnrolmentConnector
          )
            .getRegistration(req, mockAppConfig)
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
