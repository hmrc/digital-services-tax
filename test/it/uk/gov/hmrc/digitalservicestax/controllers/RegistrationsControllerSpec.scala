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
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.http.{HeaderNames, MimeTypes}
import play.api.http.Status.CREATED
import play.api.i18n.Lang.defaultLang
import play.api.i18n.{Messages, MessagesApi, MessagesImpl}
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.{AnyContent, AnyContentAsJson, BodyParser, DefaultMessagesActionBuilderImpl, DefaultMessagesControllerComponents, PlayBodyParsers, Request, RequestHeader, Result}
import play.api.test.{FakeRequest, Injecting}
import play.api.test.Helpers.{contentAsJson, contentAsString, defaultAwaitTimeout, status, stubBodyParser, stubControllerComponents}
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.digitalservicestax.actions.{LoggedInAction, LoggedInRequest, RegisteredOrPending}
import uk.gov.hmrc.digitalservicestax.backend_data.RegistrationResponse
import uk.gov.hmrc.digitalservicestax.controllers.RegistrationsController
import uk.gov.hmrc.digitalservicestax.data.BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.data.{AddressLine, Company, CompanyName, CompanyRegWrapper, ContactDetails, DSTRegNumber, Email, FormBundleNumber, InternalId, PhoneNumber, Postcode, Registration, RestrictiveString, SafeId, UTR, UkAddress}
import uk.gov.hmrc.digitalservicestax.services.{AuditingHelper, MongoPersistence}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import unit.uk.gov.hmrc.digitalservicestax.util.TestInstances._

import java.time.LocalDate
import scala.concurrent.Future

class RegistrationsControllerSpec
    extends ControllerBaseSpec
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with ScalaFutures
    with IntegrationPatience {

  val mongoPersistence: MongoPersistence           = app.injector.instanceOf[MongoPersistence]
  val mockRegisteredOrPending: RegisteredOrPending = mock[RegisteredOrPending]
  implicit val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  implicit val messages: Messages = MessagesImpl(defaultLang, messagesApi)
  implicit val mat: Materializer = app.materializer


  def controller(loginAction: LoggedInAction = loginReturn()): RegistrationsController =
    new RegistrationsController(
      authConnector = mockAuthConnector,
      cc = stubControllerComponents(
        bodyParser = stubBodyParser(AnyContentAsJson(Json.toJson(givenRegistration(SafeId("XE0001234567890"))))),
        playBodyParsers = PlayBodyParsers()(mat)
      ),
      registrationConnector = mockRegistrationConnector,
      rosmConnector = mockRosmConnector,
      taxEnrolmentConnector = mockTaxEnrolmentsConnector,
      emailConnector = mockEmailConnector,
      persistence = mongoPersistence,
      taxEnrolmentService = mockTaxEnrolmentService,
      auditing = mockAuditing,
      loggedIn = loginAction,
      appConfig = mockAppConfig,
      registrationOrPending = mockRegisteredOrPending,
      getDstNumberService = mockGetDstNumberService
    )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockRegisteredOrPending, mockAppConfig, mockTaxEnrolmentService)
  }

//  "RegistrationsController.lookupRegistration()" must {
//
//    "return 200 with registration data when dstNewSolutionFeatureFlag is false and registrationNumber has data" in {
//      val dstNumber    = Arbitrary.arbitrary[DSTRegNumber].sample.value
//      val registration = Arbitrary.arbitrary[Registration].sample.value.copy(registrationNumber = Some(dstNumber))
//      val internalId   = Arbitrary.arbitrary[InternalId].sample.value
//
//      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn false
//
//      when(mockRegisteredOrPending.getRegistration(any(), any())(any())) thenReturn Future.successful(
//        Some(registration)
//      )
//
//      val chain = for {
//        r <- mongoPersistence.registrations.update(internalId, registration)
//      } yield r
//
//      whenReady(chain) { _ =>
//        val result: Future[Result] = controller(loginReturn(internalId)).lookupRegistration().apply(FakeRequest())
//        val resultStatus           = status(result)
//
//        resultStatus mustBe 200
//        contentAsString(result) mustBe s"${Json.toJson(registration)}"
//      }
//    }
//
//    "return 200 with registration data when dstNewSolutionFeatureFlag is false and registrationNumber is empty and FormBundleNumber is defined" in {
//      val registration     = Arbitrary.arbitrary[Registration].sample.value.copy(registrationNumber = None)
//      val internalId       = Arbitrary.arbitrary[InternalId].sample.value
//      val formBundleNumber = Arbitrary.arbitrary[FormBundleNumber].sample.value
//
//      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn false
//
//      when(mockRegisteredOrPending.getRegistration(any(), any())(any())) thenReturn Future.successful(
//        Some(registration)
//      )
//
//      val chain = for {
//        r <- mongoPersistence.registrations.update(internalId, registration)
//        m <- mongoPersistence.pendingCallbacks.update(formBundleNumber, internalId)
//      } yield m
//
//      whenReady(chain) { _ =>
//        val result: Future[Result] = controller(loginReturn(internalId)).lookupRegistration().apply(FakeRequest())
//        val resultStatus           = status(result)
//
//        resultStatus mustBe 200
//        contentAsString(result) mustBe s"${Json.toJson(registration)}"
//      }
//
//    }
//
//    "return 200 with registration data when dstNewSolutionFeatureFlag is true and registration details with registrationNumber exists" in {
//      val dstNumber        = Arbitrary.arbitrary[DSTRegNumber].sample.value
//      val internalId       = Arbitrary.arbitrary[InternalId].sample.value
//      val formBundleNumber = Arbitrary.arbitrary[FormBundleNumber].sample.value
//      val registration     = Arbitrary.arbitrary[Registration].sample.value.copy(registrationNumber = Some(dstNumber))
//
//      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn true
//
//      when(mockRegisteredOrPending.getRegistration(any(), any())(any())) thenReturn Future.successful(
//        Some(registration)
//      )
//
//      val chain = for {
//        r <- mongoPersistence.registrations.update(internalId, registration)
//        m <- mongoPersistence.pendingCallbacks.update(formBundleNumber, internalId)
//      } yield m
//
//      whenReady(chain) { _ =>
//        val result: Future[Result] = controller(
//          loginReturn(
//            internalId,
//            Enrolments(
//              Set(Enrolment("HMRC-DST-ORG", Seq(EnrolmentIdentifier("DSTRefNumber", dstNumber)), "Activated"))
//            )
//          )
//        ).lookupRegistration().apply(FakeRequest())
//        val resultStatus           = status(result)
//
//        resultStatus mustBe 200
//        contentAsString(result) mustBe s"${Json.toJson(registration)}"
//      }
//    }
//
//    "return 404 when dstNewSolutionFeatureFlag is false and no registration data in DB" in {
//
//      mockAuth()
//
//      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn false
//
//      when(mockRegisteredOrPending.getRegistration(any(), any())(any())) thenReturn Future.successful(None)
//
//      val result: Future[Result] = controller().lookupRegistration().apply(FakeRequest())
//      val resultStatus           = status(result)
//
//      resultStatus mustBe 404
//    }
//
//    "return 404 when dstNewSolutionFeatureFlag is true and tax enrolments connector does not return DstRegNumber" in {
//      mockAuth()
//
//      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn true
//
//      when(mockRegisteredOrPending.getRegistration(any(), any())(any())) thenReturn Future.successful(None)
//
//      val result: Future[Result] = controller().lookupRegistration().apply(FakeRequest())
//      val resultStatus           = status(result)
//
//      resultStatus mustBe 404
//    }
//  }
//
//  "RegistrationsController.getTaxEnrolmentsPendingRegDetails()" must {
//
//    "return 200 with registration data when dstNewSolutionFeatureFlag is true and DST enrolment is in pending state" in {
//      val dstNumber  = Arbitrary.arbitrary[DSTRegNumber].sample.value
//      val internalId = Arbitrary.arbitrary[InternalId].sample.value
//
//      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn true
//
//      when(mockTaxEnrolmentService.getPendingDSTRegistration(any())(any(), any())) thenReturn Future
//        .successful(Ok)
//
//      val result: Future[Result] =
//        controller(loginReturn(internalId)).getTaxEnrolmentsPendingRegDetails().apply(FakeRequest())
//      val resultStatus           = status(result)
//
//      resultStatus mustBe 200
//    }
//
//    "return 404 with registration data when dstNewSolutionFeatureFlag is true and DST enrolment is in non-pending state" in {
//      val internalId = Arbitrary.arbitrary[InternalId].sample.value
//
//      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn true
//
//      when(mockTaxEnrolmentService.getPendingDSTRegistration(any())(any(), any())) thenReturn Future.successful(
//        NotFound
//      )
//
//      val result: Future[Result] =
//        controller(loginReturn(internalId)).getTaxEnrolmentsPendingRegDetails().apply(FakeRequest())
//      val resultStatus           = status(result)
//
//      resultStatus mustBe 404
//    }
//
//    "return 404 with None when dstNewSolutionFeatureFlag is false" in {
//      val internalId = Arbitrary.arbitrary[InternalId].sample.value
//
//      when(mockAppConfig.dstNewSolutionFeatureFlag) thenReturn false
//
//      val result: Future[Result] =
//        controller(loginReturn(internalId)).getTaxEnrolmentsPendingRegDetails().apply(FakeRequest())
//      val resultStatus           = status(result)
//
//      resultStatus mustBe 404
//    }
//  }

  "RegistrationsController.submitRegistration()" must {
    val regController = controller(loginReturnWithReg())

    "return the updated registration upon successful registration" in {
      // Given
      val companySafeId = SafeId("XE0001234567890")
      val submittedRegistration     = givenRegistration(companySafeId)
      val formBundleNumber = FormBundleNumber("677499783421")
      val registrationResponse = RegistrationResponse(LocalDate.now().toString, formBundleNumber)
      val expectedExtendedDataEvent = AuditingHelper.buildRegistrationAudit(submittedRegistration, "providerId", Some(formBundleNumber), "SUCCESS")

      mockAuth()
      when(mockAppConfig.taxEnrolmentsEnabled).thenReturn(true)
      when(mockRosmConnector.getSafeId(submittedRegistration)(hc, regController.ec)).thenReturn(Future.successful(Some(companySafeId)))
      when(mockRegistrationConnector.send("safe", companySafeId, submittedRegistration)(hc, regController.ec)).thenReturn(Future.successful(registrationResponse))
      when(mockTaxEnrolmentsConnector.subscribe(companySafeId, formBundleNumber)(hc, regController.ec)).thenReturn(Future.successful(HttpResponse(CREATED, "")))
      when(mockAuditing.sendExtendedEvent(expectedExtendedDataEvent)(hc, regController.ec)).thenReturn(Future.successful(AuditResult.Success))
      when(mockEmailConnector.sendSubmissionReceivedEmail(submittedRegistration.contact, submittedRegistration.companyReg.company.name, submittedRegistration.ultimateParent)(hc, regController.ec)).thenReturn(Future.unit)

      // When
      implicit val mat: Materializer = app.materializer
      val fakeRequest: FakeRequest[Registration] =
        FakeRequest()
          .withMethod("POST")
          .withBody(submittedRegistration)
          .withHeaders(HeaderNames.CONTENT_TYPE -> "application/json; charset=UTF-8")
      val result: Future[Result] = regController.submitRegistration().apply(fakeRequest).run()

      // Then
      println(contentAsString(result))
      status(result) mustEqual CREATED

      // test that registrations collection update was called with registration

      // test that the pending callbacks collection update was called
    }

    "must return nothing when failing to retrieve the safe id" in {
      fail("not yet implemented")
    }

    "must not await callback when failing to send registration to the data exchange service" in {
      fail("not yet implemented")
    }

    "must leave registration stuck in a pending state if subscribing to tax enrolments fails" in {
      fail("not yet implemented")
    }
  }

  private def givenRegistration(safeId: SafeId) = {
    Registration(
      CompanyRegWrapper(
        Company(CompanyName("Test Solutions Ltd"), UkAddress(AddressLine("Test Line 1"), None, None, None, Postcode("NW11 4RP"))),
        Some(UTR("1234567890")),
        Some(safeId),
        useSafeId = true,
        None
      ),
      Some(UkAddress(AddressLine("Test Line 2"), None, None, None, Postcode("NW8 5AX"))),
      Some(Company(CompanyName("Ultimate Test Solutions Ltd"), UkAddress(AddressLine("Test Line 3"), None, None, None, Postcode("NW11 4XP")))),
      ContactDetails(RestrictiveString("Tom"), RestrictiveString("Riddle"), PhoneNumber("07340507800"), Email("tom.riddle@gmail.com")),
      LocalDate.now().minusWeeks(50),
      LocalDate.now().minusWeeks(50),
      None
    )
  }

  def loginReturnWithReg(): LoggedInAction =
    new LoggedInAction(mockMcc, mockAppConfig, mockAuthConnector) {
      override def refine[A](request: Request[A]): Future[Either[Result, LoggedInRequest[A]]] = {
        Future.successful(Right(loginReqWithReg[A]))
      }

      override def parser: BodyParser[AnyContent] = {
        stubBodyParser(AnyContentAsJson(Json.toJson(givenRegistration(SafeId("XE0001234567890")))))
      }
    }

  def loginReqWithReg[A]: LoggedInRequest[A] = new LoggedInRequest[A](
    InternalId("Int-aaff66"),
    mockEnrolments,
    "",
    Some("groupId"),
    FakeRequest()
      .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
      .withMethod("POST")
      .withBody(givenRegistration(SafeId("XE0001234567890")).asInstanceOf[A])
  )
}
