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

import it.uk.gov.hmrc.digitalservicestax.controllers.actions.FakeIdentifierAction
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsJson, Result, Results}
import play.api.test.{FakeRequest, Helpers}
import play.mvc.Http.HttpVerbs
import uk.gov.hmrc.digitalservicestax.controllers.routes
import uk.gov.hmrc.digitalservicestax.data.{AddressLine, Company, CompanyName, CompanyRegWrapper, ContactDetails, Email, PhoneNumber, Postcode, Registration, RestrictiveString, SafeId, UTR, UkAddress}
import play.api.test.Helpers._
import uk.gov.hmrc.digitalservicestax.data.BackendAndFrontendJson._
import play.api.inject._
import uk.gov.hmrc.digitalservicestax.actions.{IdentifierAction, LoggedInAction}

import java.time.LocalDate
import scala.concurrent.Future

class RegistrationsControllerSpecc
    extends PlaySpec
    with MockitoSugar
    with Results
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with ScalaFutures
    with IntegrationPatience {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(
      bind[IdentifierAction].to[FakeIdentifierAction]
    )
    .build()

  "submit registration" must {
    "return the updated registration upon successful registration" in {
      // Given
      val fakeRequest: FakeRequest[AnyContentAsJson] = FakeRequest(HttpVerbs.POST, routes.RegistrationsController.submitRegistration().url)
        .withJsonBody(Json.toJson(givenRegistration(SafeId("XE0001234567890"))))

      // When
      val result: Future[Result] = Helpers.route(app, fakeRequest).value

      // Then
      status(result) mustEqual CREATED
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
}
