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
import com.github.tomakehurst.wiremock.client.WireMock.{equalTo, equalToJson, matchingJsonPath, postRequestedFor, urlEqualTo, verify}
import it.uk.gov.hmrc.digitalservicestax.controllers.actions.FakeIdentifierRegistrationAction.providerId
import it.uk.gov.hmrc.digitalservicestax.controllers.actions.{FakeIdentifierRegisteredAction, FakeIdentifierRegistrationAction}
import it.uk.gov.hmrc.digitalservicestax.util.{AuditingEmailStubs, ReturnsWireMockStubs, WiremockServer}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsNull, Json}
import play.api.mvc.{AnyContentAsJson, Result, Results}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import play.mvc.Http.HttpVerbs
import uk.gov.hmrc.digitalservicestax.actions.{IdentifierAction, RegisteredActionRefiner}
import uk.gov.hmrc.digitalservicestax.controllers.routes
import uk.gov.hmrc.digitalservicestax.data.Activity.{OnlineMarketplace, SearchEngine, SocialMedia}
import uk.gov.hmrc.digitalservicestax.data.BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.data.{AccountName, CompanyName, ForeignBankAccount, GroupCompany, IBAN, Money, Percent, Period, RepaymentDetails, Return, UTR}
import uk.gov.hmrc.digitalservicestax.services.{AuditingHelper, MongoPersistence}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import scala.collection.immutable.ListMap
import scala.concurrent.Future

class SubmitReturnsControllerSpec
    extends PlaySpec
    with MockitoSugar
    with Results
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with ScalaFutures
    with IntegrationPatience
    with WiremockServer
    with ReturnsWireMockStubs
    with AuditingEmailStubs {

  val defaultConfiguration: Map[String, Any] = Map(
    "microservice.services.des.port" -> WireMockSupport.port,
    "auditing.enabled"               -> "true",
    "auditing.consumer.baseUri.host" -> "localhost",
    "auditing.consumer.baseUri.port" -> WireMockSupport.port
  )

  override lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(
      bind[IdentifierAction].to[FakeIdentifierRegistrationAction],
      bind[RegisteredActionRefiner].to[FakeIdentifierRegisteredAction]
    )
    .configure(defaultConfiguration)
    .build()

  override def beforeEach(): Unit = {
    app.injector.instanceOf[MongoPersistence].registrations.repository().collection.drop().head()
    app.injector.instanceOf[MongoPersistence].pendingCallbacks.repository().collection.drop().head()
    WireMock.reset()
  }

  "submit return" must {
    "submit a valid return" in {
      // Given
      val submitReturn = givenReturn()
      val dstRegNo     = FakeIdentifierRegisteredAction.givenRegistration.registrationNumber.head

      stubGetPeriodsSuccess(dstRegNo)
      stubReturnSendSuccess(dstRegNo)
      stubAuditWrite

      val fakeRequest: FakeRequest[AnyContentAsJson] =
        FakeRequest(HttpVerbs.POST, routes.ReturnsController.submitReturn("001").url)
          .withJsonBody(Json.toJson(submitReturn))
          .withHeaders("Authorization" -> "Bearer 1234")

      // When
      val result: Future[Result] = Helpers.route(app, fakeRequest).value

      // Then
      status(result) mustEqual OK
      contentAsJson(result) mustEqual JsNull

      whenReady(
        app.injector
          .instanceOf[MongoPersistence]
          .returns
          .get(FakeIdentifierRegisteredAction.givenRegistration, Period.Key("001"))
      ) { optRetWrapper =>
        optRetWrapper.head mustEqual submitReturn
      }

      verify(
        postRequestedFor(urlEqualTo("/write/audit")).withRequestBody(
          matchingJsonPath("$.auditType", equalTo("returnSubmissionResponse"))
        )
      )

      verify(
        postRequestedFor(urlEqualTo("/write/audit")).withRequestBody(
          matchingJsonPath("$.auditType", equalTo("returnSubmitted"))
        )
      )

      // TODO receivedAt needs to be exlucded in the comparison for this to work
      /*implicit val hc: HeaderCarrier = HeaderCarrier()
      val expectedEventDetail = AuditingHelper
        .buildReturnSubmissionAudit(
          dstRegNo,
          providerId,
          Period(
            LocalDate.parse("2021-02-02"),
            LocalDate.parse("2021-03-03"),
            LocalDate.parse("2021-02-02"),
            Period.Key("001")
          ),
          submitReturn,
          isAmend = false
        )
        .detail
        .toString()

      verify(
        postRequestedFor(urlEqualTo("/write/audit")).withRequestBody(
          matchingJsonPath("$.detail", equalToJson(expectedEventDetail))
        )
      )*/
    }

    "return Not Found when a period key for the return doesn't come back" in {
      // Given
      val submitReturn = givenReturn()
      val dstRegNo     = FakeIdentifierRegisteredAction.givenRegistration.registrationNumber.head

      stubGetPeriodsSuccess(dstRegNo)
      stubReturnSendSuccess(dstRegNo)
      stubAuditWrite

      val fakeRequest: FakeRequest[AnyContentAsJson] =
        FakeRequest(HttpVerbs.POST, routes.ReturnsController.submitReturn("002").url)
          .withJsonBody(Json.toJson(submitReturn))
          .withHeaders("Authorization" -> "Bearer 1234")

      // When
      val result: Future[Result] = Helpers.route(app, fakeRequest).value

      // Then
      status(result) mustEqual NOT_FOUND
    }

    "return Internal Server Error when fetching the return periods" in {
      // Given
      val submitReturn = givenReturn()
      val dstRegNo     = FakeIdentifierRegisteredAction.givenRegistration.registrationNumber.head

      stubGetPeriodsError(dstRegNo)
      stubReturnSendSuccess(dstRegNo)
      stubAuditWrite

      val fakeRequest: FakeRequest[AnyContentAsJson] =
        FakeRequest(HttpVerbs.POST, routes.ReturnsController.submitReturn("002").url)
          .withJsonBody(Json.toJson(submitReturn))
          .withHeaders("Authorization" -> "Bearer 1234")

      // When
      val caught: Exception = intercept[Exception] {
        await(Helpers.route(app, fakeRequest).value)
      }

      // Then
      caught.getMessage mustEqual s"GET of 'http://localhost:11111/enterprise/obligation-data/zdst/AMDST0799721562/DST?from=2020-04-01&to=${LocalDate.now().plusYears(1)}' returned 500. Response body: ''"

      verify(
        postRequestedFor(urlEqualTo("/write/audit")).withRequestBody(
          matchingJsonPath("$.auditType", equalTo("returnSubmissionResponse"))
        )
      )

      val expectedEventDetail = Json
        .obj(
          "responseStatus" -> "ERROR",
          "errorReason"    -> caught.getMessage
        )
        .toString()

      verify(
        postRequestedFor(urlEqualTo("/write/audit")).withRequestBody(
          matchingJsonPath("$.detail", equalToJson(expectedEventDetail))
        )
      )
    }
  }

  private def givenReturn() =
    Return(
      Set(SearchEngine),
      Map(SocialMedia                                                                  -> Percent(13), OnlineMarketplace -> Percent(45)),
      Money(64934071),
      Some(Money(3812176)),
      ListMap(GroupCompany(CompanyName("Test Solutions Ltd"), Some(UTR("1234567890"))) -> Money(44146909)),
      Money(BigDecimal(62565784)),
      Some(RepaymentDetails(AccountName("aaaaaaaaaaaaaaaa"), ForeignBankAccount(IBAN("LB52372840451692007088329912"))))
    )
}
