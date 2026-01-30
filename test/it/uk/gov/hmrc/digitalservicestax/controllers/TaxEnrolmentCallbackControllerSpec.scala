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

package it.uk.gov.hmrc.digitalservicestax.controllers

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{absent, equalTo, equalToJson, getRequestedFor, matchingJsonPath, postRequestedFor, urlEqualTo, urlPathEqualTo, verify}
import it.uk.gov.hmrc.digitalservicestax.controllers.actions.FakeIdentifierRegistrationAction
import it.uk.gov.hmrc.digitalservicestax.controllers.actions.FakeIdentifierRegistrationAction.internalId
import it.uk.gov.hmrc.digitalservicestax.util.{AuditingEmailStubs, TaxEnrolmentCallbackWireMockStubs, WiremockServer}
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.result.InsertOneResult
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.*
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import play.mvc.Http.HttpVerbs
import uk.gov.hmrc.digitalservicestax.actions.IdentifierAction
import uk.gov.hmrc.digitalservicestax.controllers.{CallbackNotification, routes}
import uk.gov.hmrc.digitalservicestax.data
import uk.gov.hmrc.digitalservicestax.data.{AddressLine, Company, CompanyName, CompanyRegWrapper, ContactDetails, DSTRegNumber, Email, FormBundleNumber, PhoneNumber, Postcode, Registration, RestrictiveString, SafeId, UTR, UkAddress}
import uk.gov.hmrc.digitalservicestax.services.{AuditingHelper, MongoPersistence}
import uk.gov.hmrc.digitalservicestax.services.MongoPersistence.{CallbackWrapper, RegWrapper}

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TaxEnrolmentCallbackControllerSpec
    extends PlaySpec
    with MockitoSugar
    with Results
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with ScalaFutures
    with IntegrationPatience
    with WiremockServer
    with TaxEnrolmentCallbackWireMockStubs
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

  val formBundleNumber: data.FormBundleNumber = FormBundleNumber("123456789112")
  val safeId: data.SafeId                     = SafeId("XE0001234567890")

  override lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(
      bind[IdentifierAction].to[FakeIdentifierRegistrationAction]
    )
    .configure(defaultConfiguration)
    .build()

  override def beforeEach(): Unit = {
    app.injector.instanceOf[MongoPersistence].registrations.repository().collection.drop().head()
    app.injector.instanceOf[MongoPersistence].pendingCallbacks.repository().collection.drop().head()
    WireMock.reset()
  }

  "TaxEnrolmentCallbackController.callback" must {
    "process the DST Registration when the state of the enrolment from Tax Enrolments is SUCCEEDED" in {
      whenReady(setupPendingRegistrationData()) { _ =>
        // Given
        val dstRegNumber         = DSTRegNumber("AMDST0799721562")
        stubTaxEnrolmentsSubscribeEndpointSuccess(formBundleNumber, dstRegNumber)
        stubGetPeriodsSuccess(dstRegNumber)
        stubAuditWrite
        stubEmailSendEndpointSuccess
        val callbackNotification = CallbackNotification("SUCCEEDED", None)

        // When
        val fakeRequest =
          FakeRequest(HttpVerbs.POST, routes.TaxEnrolmentCallbackController.callback(formBundleNumber.value).url)
            .withJsonBody(Json.toJson(callbackNotification))
        val result      = Helpers.route(app, fakeRequest).value

        // Then
        status(result) mustEqual NO_CONTENT

        val mongoPersistence: MongoPersistence = app.injector.instanceOf[MongoPersistence]
        whenReady(mongoPersistence.pendingCallbacks.get(formBundleNumber)) { optInternalId =>
          optInternalId mustEqual None
        }

        whenReady(mongoPersistence.registrations.get(internalId)) { optReg =>
          optReg.head.registrationNumber.head mustEqual dstRegNumber
        }

        verify(
          postRequestedFor(urlEqualTo("/write/audit")).withRequestBody(
            matchingJsonPath("$.auditSource", equalTo("digital-services-tax"))
          )
        )

        verify(
          postRequestedFor(urlEqualTo("/write/audit")).withRequestBody(
            matchingJsonPath("$.auditType", equalTo("digitalServicesTaxEnrolmentResponse"))
          )
        )

        val expectedEventDetail = Json
          .obj(
            "subscriptionId"        -> formBundleNumber.value,
            "dstRegistrationNumber" -> dstRegNumber.value,
            "outcome"               -> "SUCCESS"
          )
          .toString()

        verify(
          postRequestedFor(urlEqualTo("/write/audit")).withRequestBody(
            matchingJsonPath("$.detail", equalToJson(expectedEventDetail))
          )
        )
      }
    }

    "not process the DST Registration when the subscription state of the enrolment from Tax Enrolments is not succeeded" in {
      whenReady(setupPendingRegistrationData()) { _ =>
        // Given
        val dstRegNumber         = DSTRegNumber("AMDST0799721562")
        stubTaxEnrolmentsSubscribeEndpointSuccess(formBundleNumber, dstRegNumber)
        stubAuditWrite
        val callbackNotification = CallbackNotification("EnrolmentError", None)

        // When
        val fakeRequest =
          FakeRequest(HttpVerbs.POST, routes.TaxEnrolmentCallbackController.callback(formBundleNumber.value).url)
            .withJsonBody(Json.toJson(callbackNotification))
        val result      = Helpers.route(app, fakeRequest).value

        // Then
        status(result) mustEqual NO_CONTENT

        val mongoPersistence: MongoPersistence = app.injector.instanceOf[MongoPersistence]
        whenReady(mongoPersistence.pendingCallbacks.get(formBundleNumber)) { optInternalId =>
          optInternalId.value mustEqual internalId
        }

        whenReady(mongoPersistence.registrations.get(internalId)) { optReg =>
          optReg.head.registrationNumber mustEqual None
        }

        verify(0, getRequestedFor(urlEqualTo(s"""/tax-enrolments/subscriptions/${formBundleNumber.value}""")))
        verify(0, getRequestedFor(urlPathEqualTo(s"""/enterprise/obligation-data/zdst/""")))

        verify(
          postRequestedFor(urlEqualTo("/write/audit")).withRequestBody(
            matchingJsonPath("$.auditSource", equalTo("digital-services-tax"))
          )
        )

        val expectedEventDetail = Json
          .obj(
            "subscriptionId" -> formBundleNumber.value,
            "outcome"        -> "ERROR"
          )
          .toString()

        verify(
          postRequestedFor(urlEqualTo("/write/audit")).withRequestBody(
            matchingJsonPath("$.detail", equalToJson(expectedEventDetail))
          )
        )
      }
    }

    "not process the DST registration when the next pending period cannot be fetched because correspondence date is received" ignore {
      whenReady(setupPendingRegistrationData()) { _ =>
        // Given
        val dstRegNumber         = DSTRegNumber("AMDST0799721562")
        stubTaxEnrolmentsSubscribeEndpointSuccess(formBundleNumber, dstRegNumber)
        stubGetPeriodsError(dstRegNumber)
        stubAuditWrite
        val callbackNotification = CallbackNotification("SUCCEEDED", None)

        // When
        val fakeRequest =
          FakeRequest(HttpVerbs.POST, routes.TaxEnrolmentCallbackController.callback(formBundleNumber.value).url)
            .withJsonBody(Json.toJson(callbackNotification))
        val result      = Helpers.route(app, fakeRequest).value

        // Then
        status(result) mustEqual NO_CONTENT

        val mongoPersistence: MongoPersistence = app.injector.instanceOf[MongoPersistence]
        whenReady(mongoPersistence.pendingCallbacks.get(formBundleNumber)) { optInternalId =>
          optInternalId mustEqual None
        }

        whenReady(mongoPersistence.registrations.get(internalId)) { optReg =>
          optReg.head.registrationNumber.head mustEqual dstRegNumber
        }

        verify(0, postRequestedFor(urlEqualTo("/hmrc/email")))

        verify(
          postRequestedFor(urlEqualTo("/write/audit")).withRequestBody(
            matchingJsonPath("$.auditSource", equalTo("digital-services-tax"))
          )
        )

        // The fact that it doesn't send any kind of audit in this case is bizarre
        verify(
          postRequestedFor(urlEqualTo("/write/audit")).withRequestBody(
            matchingJsonPath("$.[?(@.auditType == 'digitalServicesTaxEnrolmentResponse')]", absent())
          )
        )
      }
    }

  }

  private def setupPendingRegistrationData(): Future[InsertOneResult] =
    app.injector
      .instanceOf[MongoPersistence]
      .registrations
      .repository()
      .collection
      .insertOne(
        RegWrapper(internalId, givenRegistration(Some(safeId)))
      )
      .toFuture()
      .flatMap(insertOneResult =>
        app.injector
          .instanceOf[MongoPersistence]
          .pendingCallbacks
          .repository()
          .collection
          .insertOne(CallbackWrapper(internalId, formBundleNumber))
          .toFuture()
      )

  private def givenRegistration(safeId: Option[SafeId]) =
    Registration(
      CompanyRegWrapper(
        Company(
          CompanyName("Test Solutions Ltd"),
          UkAddress(AddressLine("Test Line 1"), None, None, None, Postcode("NW11 4RP"))
        ),
        Some(UTR("1234567890")),
        safeId,
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
