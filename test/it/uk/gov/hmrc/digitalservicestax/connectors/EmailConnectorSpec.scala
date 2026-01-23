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

package it.uk.gov.hmrc.digitalservicestax.connectors

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, stubFor, urlPathEqualTo}
import it.uk.gov.hmrc.digitalservicestax.util.TestInstances._
import it.uk.gov.hmrc.digitalservicestax.util.{FakeApplicationSetup, WiremockServer}
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.digitalservicestax.connectors.EmailConnector
import uk.gov.hmrc.digitalservicestax.data.{CompanyName, ContactDetails, DSTRegNumber, Period}
import uk.gov.hmrc.http.HeaderCarrier

class EmailConnectorSpec extends FakeApplicationSetup with WiremockServer with ScalaCheckDrivenPropertyChecks {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.email.port" -> WireMockSupport.port
    )
    .build()

  object EmailTestConnector extends EmailConnector(httpClient, appConfig)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "should get no response back if des is not available" in {
    implicit def arbCompanyName: Arbitrary[CompanyName] = Arbitrary(CompanyName.gen)
    val contactDetails                                  = arbitrary[ContactDetails].sample.value
    val companyName                                     = arbitrary[CompanyName].sample.value
    val parentRef                                       = arbitrary[CompanyName].sample.value
    val dstNumber                                       = arbitrary[DSTRegNumber].sample.value
    val period                                          = arbitrary[Period].sample.value

    stubFor(
      post(urlPathEqualTo("/hmrc/email"))
        .willReturn(
          aResponse()
            .withStatus(200)
        )
    )

    val response = EmailTestConnector.sendConfirmationEmail(contactDetails, companyName, parentRef, dstNumber, period)

    whenReady(response) { res => }

  }

  "should send a confirmation email for a submission received" in {
    val contactDetails = arbitrary[ContactDetails].sample.value
    val companyName    = arbitrary[CompanyName].sample.value

    stubFor(
      post(urlPathEqualTo("/hmrc/email"))
        .willReturn(
          aResponse()
            .withStatus(200)
        )
    )

    val response = EmailTestConnector.sendSubmissionReceivedEmail(contactDetails, companyName, None)

    whenReady(response) { res => }

  }

}
