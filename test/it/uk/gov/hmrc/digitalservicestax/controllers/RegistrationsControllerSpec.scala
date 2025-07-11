/*
 * Copyright 2025 HM Revenue & Customs
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

import it.uk.gov.hmrc.digitalservicestax.controllers.actions.FakeIdentifierRegistrationAction
import it.uk.gov.hmrc.digitalservicestax.util.{AuditingEmailStubs, RegistrationWiremockStubs, WiremockServer}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsJson, Result, Results}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import play.mvc.Http.HttpVerbs
import uk.gov.hmrc.digitalservicestax.actions.IdentifierAction
import uk.gov.hmrc.digitalservicestax.backend_data.RegistrationResponse
import uk.gov.hmrc.digitalservicestax.controllers.routes
import uk.gov.hmrc.digitalservicestax.data.BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.data.{AddressLine, Company, CompanyName, CompanyRegWrapper, ContactDetails, DSTRegNumber, Email, FormBundleNumber, PhoneNumber, Postcode, Registration, RestrictiveString, SafeId, UTR, UkAddress}
import uk.gov.hmrc.digitalservicestax.services.MongoPersistence
import uk.gov.hmrc.digitalservicestax.services.MongoPersistence.{CallbackWrapper, RegWrapper}

import java.time.LocalDate
import scala.concurrent.Future

class RegistrationsControllerSpec
    extends PlaySpec
    with MockitoSugar
    with Results
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with ScalaFutures
    with IntegrationPatience
    with WiremockServer
    with RegistrationWiremockStubs
    with AuditingEmailStubs {

  val defaultConfiguration: Map[String, Any] = Map(
    "microservice.services.des.port"                   -> WireMockSupport.port,
    "microservice.services.email.port"                 -> WireMockSupport.port,
    "microservice.services.tax-enrolments.port"        -> WireMockSupport.port,
    "microservice.services.enrolment-store-proxy.port" -> WireMockSupport.port,
    "tax-enrolments.enabled"                           -> "true",
    "auditing.enabled"                                 -> "true",
    "auditing.consumer.baseUri.host"                   -> "localhost",
    "auditing.consumer.baseUri.port"                   -> WireMockSupport.port
  )

  override lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(
      bind[IdentifierAction].to[FakeIdentifierRegistrationAction]
    )
    .configure(defaultConfiguration)
    .build()

  def defaultAppWithDstNewSolutionFeatureFalse(): Application =
    new GuiceApplicationBuilder()
      .overrides(
        bind[IdentifierAction].to[FakeIdentifierRegistrationAction]
      )
      .configure(defaultConfiguration.+(("feature.dstNewProposedSolution", false)))
      .build()

  def defaultAppWithDstNewSolutionFeatureTrue(): Application =
    new GuiceApplicationBuilder()
      .overrides(
        bind[IdentifierAction].to[FakeIdentifierRegistrationAction]
      )
      .configure(defaultConfiguration.+(("feature.dstNewProposedSolution", true)))
      .build()

  override def beforeEach(): Unit = {
    app.injector.instanceOf[MongoPersistence].registrations.repository().collection.drop().head()
    app.injector.instanceOf[MongoPersistence].pendingCallbacks.repository().collection.drop().head()
  }

  "submit registration" must {
    "return the updated registration upon successful registration" in {
      // Given
      val safeId                  = SafeId("XE0001234567890")
      val registration            = givenRegistration(safeId)
      val formBundleNumber        = FormBundleNumber("123456789112")
      val desRegistrationResponse = RegistrationResponse(LocalDate.now.toString, formBundleNumber)

      stubDesRegEndpointSuccess(safeId, formBundleNumber, Some(desRegistrationResponse))
      stubRetrieveROSMDetailsWithoutIDEndpointSuccess(safeId)
      stubTaxEnrolmentsSubscribeEndpointSuccess(formBundleNumber)
      stubEmailSendEndpointSuccess
      stubAuditWrite

      val fakeRequest: FakeRequest[AnyContentAsJson] =
        FakeRequest(HttpVerbs.POST, routes.RegistrationsController.submitRegistration().url)
          .withJsonBody(Json.toJson(registration))

      // When
      val result: Future[Result] = Helpers.route(app, fakeRequest).value

      // Then
      status(result) mustEqual OK
      contentAsJson(result) mustEqual Json.toJson(desRegistrationResponse)

      val mongoPersistence: MongoPersistence = app.injector.instanceOf[MongoPersistence]
      whenReady(mongoPersistence.registrations.get(FakeIdentifierRegistrationAction.internalId)) { optReg =>
        optReg.value mustEqual registration
      }

      whenReady(mongoPersistence.pendingCallbacks.get(formBundleNumber)) { optInternalId =>
        optInternalId.value mustEqual FakeIdentifierRegistrationAction.internalId
      }
    }

    "must leave registration stuck in a pending state if subscribing to tax enrolments fails" in {
      // Given
      val safeId                  = SafeId("XE0001234567890")
      val registration            = givenRegistration(safeId)
      val formBundleNumber        = FormBundleNumber("123456789112")
      val desRegistrationResponse = RegistrationResponse(LocalDate.now.toString, formBundleNumber)

      stubDesRegEndpointSuccess(safeId, formBundleNumber, Some(desRegistrationResponse))
      stubRetrieveROSMDetailsWithoutIDEndpointSuccess(safeId)
      stubTaxEnrolmentsSubscribeEndpointError(formBundleNumber)
      stubEmailSendEndpointSuccess
      stubAuditWrite

      val fakeRequest: FakeRequest[AnyContentAsJson] =
        FakeRequest(HttpVerbs.POST, routes.RegistrationsController.submitRegistration().url)
          .withJsonBody(Json.toJson(registration))

      // When
      val result: Future[Result] = Helpers.route(app, fakeRequest).value

      // Then
      status(result) mustEqual OK

      val mongoPersistence: MongoPersistence = app.injector.instanceOf[MongoPersistence]
      whenReady(mongoPersistence.registrations.get(FakeIdentifierRegistrationAction.internalId)) { optReg =>
        optReg.value mustEqual registration
      }

      whenReady(mongoPersistence.pendingCallbacks.get(formBundleNumber)) { optInternalId =>
        optInternalId.value mustEqual FakeIdentifierRegistrationAction.internalId
      }
    }
  }

  "RegistrationsController.lookupRegistration()" must {
    "return 200 with registration data when dstNewSolutionFeatureFlag is false and registrationNumber has data" in {
      val newApp = defaultAppWithDstNewSolutionFeatureFalse()

      running(newApp) {
        // Given
        val registration     =
          givenRegistration(SafeId("XE0001234567890")).copy(registrationNumber = Some(DSTRegNumber("AMDST0799721562")))
        val mongoPersistence = newApp.injector.instanceOf[MongoPersistence]
        mongoPersistence.registrations
          .repository()
          .collection
          .insertOne(RegWrapper(FakeIdentifierRegistrationAction.internalId, registration))
          .headOption()

        stubTaxEnrolmentsStoreProxyDstRefSuccess("123456")
        stubAuditWrite

        // When
        val result = Helpers
          .route(newApp, FakeRequest(HttpVerbs.GET, routes.RegistrationsController.lookupRegistration().url))
          .value

        // Then
        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(registration)
      }
    }

    "return 200 with registration data when dstNewSolutionFeatureFlag is false and registrationNumber is empty and FormBundleNumber is defined" in {
      val newApp = defaultAppWithDstNewSolutionFeatureFalse()

      running(newApp) {
        // Given
        val registration     = givenRegistration(SafeId("XE0001234567890"))
        val formBundleNumber = FormBundleNumber("193756789113")
        val mongoPersistence = newApp.injector.instanceOf[MongoPersistence]
        mongoPersistence.registrations
          .repository()
          .collection
          .insertOne(RegWrapper(FakeIdentifierRegistrationAction.internalId, registration))
          .headOption()
        mongoPersistence.pendingCallbacks
          .repository()
          .collection
          .insertOne(CallbackWrapper(FakeIdentifierRegistrationAction.internalId, formBundleNumber))
          .headOption()

        // When
        val result = Helpers
          .route(newApp, FakeRequest(HttpVerbs.GET, routes.RegistrationsController.lookupRegistration().url))
          .value

        // Then
        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(registration)
      }
    }

    "return 200 with registration data when dstNewSolutionFeatureFlag is true and registration details with registrationNumber exists" in {
      val newApp = defaultAppWithDstNewSolutionFeatureTrue()

      running(newApp) {
        // Given
        val registration     =
          givenRegistration(SafeId("XE0001234567890")).copy(registrationNumber = Some(DSTRegNumber("AMDST0799721562")))
        val formBundleNumber = FormBundleNumber("193756789113")
        val mongoPersistence = newApp.injector.instanceOf[MongoPersistence]

        stubTaxEnrolmentsStoreProxyDstRefSuccess("123456")
        stubAuditWrite

        mongoPersistence.registrations
          .repository()
          .collection
          .insertOne(RegWrapper(FakeIdentifierRegistrationAction.internalId, registration))
          .headOption()
        mongoPersistence.pendingCallbacks
          .repository()
          .collection
          .insertOne(CallbackWrapper(FakeIdentifierRegistrationAction.internalId, formBundleNumber))
          .headOption()

        // When
        val result = Helpers
          .route(newApp, FakeRequest(HttpVerbs.GET, routes.RegistrationsController.lookupRegistration().url))
          .value

        // Then
        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(registration)
      }
    }

    "return 404 when dstNewSolutionFeatureFlag is false and no registration data in DB" in {
      val newApp = defaultAppWithDstNewSolutionFeatureFalse()

      running(newApp) {
        // Given
        stubTaxEnrolmentsStoreProxyDstRefNotFound("123456")
        stubAuditWrite

        // When
        val result = Helpers
          .route(newApp, FakeRequest(HttpVerbs.GET, routes.RegistrationsController.lookupRegistration().url))
          .value

        // Then
        status(result) mustEqual NOT_FOUND
      }
    }
  }

  private def givenRegistration(safeId: SafeId) =
    Registration(
      CompanyRegWrapper(
        Company(
          CompanyName("Test Solutions Ltd"),
          UkAddress(AddressLine("Test Line 1"), None, None, None, Postcode("NW11 4RP"))
        ),
        Some(UTR("1234567890")),
        Some(safeId),
        useSafeId = true,
        None
      ),
      Some(UkAddress(AddressLine("Test Line 2"), None, None, None, Postcode("NW8 5AX"))),
      Some(
        Company(
          CompanyName("Ultimate Test Solutions Ltd"),
          UkAddress(AddressLine("Test Line 3"), None, None, None, Postcode("NW11 4XP"))
        )
      ),
      ContactDetails(
        RestrictiveString("Tom"),
        RestrictiveString("Riddle"),
        PhoneNumber("07340507800"),
        Email("tom.riddle@gmail.com")
      ),
      LocalDate.now().minusWeeks(50),
      LocalDate.now().minusWeeks(50),
      None
    )
}
