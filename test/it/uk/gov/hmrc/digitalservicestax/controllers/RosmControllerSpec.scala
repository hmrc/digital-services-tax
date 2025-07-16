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

import it.uk.gov.hmrc.digitalservicestax.util.{AuditingEmailStubs, RosmWireMockStubs, WiremockServer}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.digitalservicestax.controllers.routes
import uk.gov.hmrc.digitalservicestax.data.BackendAndFrontendJson.companyRegWrapperFormat
import uk.gov.hmrc.digitalservicestax.data.{AddressLine, Company, CompanyName, CompanyRegWrapper, Postcode, SafeId, SapNumber, UkAddress}

class RosmControllerSpec
    extends PlaySpec
    with MockitoSugar
    with Results
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with ScalaFutures
    with IntegrationPatience
    with WiremockServer
    with AuditingEmailStubs
    with RosmWireMockStubs {

  val defaultConfiguration: Map[String, Any] = Map(
    "microservice.services.auth.port" -> WireMockSupport.port,
    "microservice.services.des.port"  -> WireMockSupport.port,
    "auditing.enabled"                -> "true",
    "auditing.consumer.baseUri.host"  -> "localhost",
    "auditing.consumer.baseUri.port"  -> WireMockSupport.port
  )

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(defaultConfiguration)
    .build()

  "RosmControllerSpec.lookupCompany" must {
    "be able to retrieve an existing company" in {
      // Given
      stubAuditWrite
      authPostAuthoriseSuccess()
      stubROSMDetailsSuccess()

      val fakeRequest = FakeRequest(Helpers.GET, routes.RosmController.lookupCompany().url)
        .withHeaders("Authorization" -> "Bearer 1234")

      // When
      val result = Helpers.route(app, fakeRequest).value

      // Then
      status(result) mustEqual Helpers.OK

      contentAsJson(result).as[CompanyRegWrapper] mustEqual CompanyRegWrapper(
        Company(
          CompanyName("Trotters Trading"),
          UkAddress(
            AddressLine("100 SuttonStreet"),
            Some(AddressLine("Wokingham")),
            Some(AddressLine("Surrey")),
            Some(AddressLine("London")),
            Postcode("DH14 1EJ")
          )
        ),
        None,
        Some(SafeId("XE0001234567890")),
        useSafeId = false,
        Some(SapNumber("1234567890"))
      )
    }

    "return not found when 404 from DES" in {
      // Given
      stubAuditWrite
      authPostAuthoriseSuccess()
      stubROSMDetailsNotFound()

      val fakeRequest = FakeRequest(Helpers.GET, routes.RosmController.lookupCompany().url)
        .withHeaders("Authorization" -> "Bearer 1234")

      // When
      val result = Helpers.route(app, fakeRequest).value

      // Then
      status(result) mustEqual Helpers.NOT_FOUND
    }
  }

  "RosmController.lookupWithIdCheckPostcode" must {
    "return the company when the postcode matches" in {
      // Given
      stubAuditWrite
      authPostAuthoriseSuccess()
      stubROSMDetailsSuccess()

      val fakeRequest = FakeRequest(Helpers.GET, routes.RosmController.lookupWithIdCheckPostcode("1111118888", "DH14 1EJ").url)
        .withHeaders("Authorization" -> "Bearer 1234")

      // When
      val result = Helpers.route(app, fakeRequest).value

      // Then
      status(result) mustEqual Helpers.OK

      contentAsJson(result).as[CompanyRegWrapper] mustEqual CompanyRegWrapper(
        Company(
          CompanyName("Trotters Trading"),
          UkAddress(
            AddressLine("100 SuttonStreet"),
            Some(AddressLine("Wokingham")),
            Some(AddressLine("Surrey")),
            Some(AddressLine("London")),
            Postcode("DH14 1EJ")
          )
        ),
        None,
        Some(SafeId("XE0001234567890")),
        useSafeId = false,
        Some(SapNumber("1234567890"))
      )
    }

    "return NOT FOUND when thet postcode does not match" in {
      // Given
      stubAuditWrite
      authPostAuthoriseSuccess()
      stubROSMDetailsNotFound()

      val fakeRequest = FakeRequest(Helpers.GET, routes.RosmController.lookupWithIdCheckPostcode("1111118888", "NW14 1AB").url)
        .withHeaders("Authorization" -> "Bearer 1234")

      // When
      val result = Helpers.route(app, fakeRequest).value

      // Then
      status(result) mustEqual Helpers.NOT_FOUND
    }
  }
}
