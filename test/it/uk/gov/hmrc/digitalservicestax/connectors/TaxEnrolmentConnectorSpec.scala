/*
 * Copyright 2022 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.outworkers.util.samplers._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.digitalservicestax.connectors.{Identifier, TaxEnrolmentConnector, TaxEnrolmentsSubscription}
import uk.gov.hmrc.digitalservicestax.data.DSTRegNumber
import uk.gov.hmrc.http.HeaderCarrier
import it.uk.gov.hmrc.digitalservicestax.util.{FakeApplicationSetup, WiremockServer}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

class TaxEnrolmentConnectorSpec extends FakeApplicationSetup with WiremockServer with ScalaCheckDrivenPropertyChecks {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.tax-enrolments.port" -> WireMockSupport.port
    )
    .build()

  object TaxTestConnector extends TaxEnrolmentConnector(
    httpClient,
    environment.mode,
    appConfig,
    testConnector
  )

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "should retrieve the latest DST period for a DSTRegNumber" in {
    val subscriptionId = gen[ShortString].value
    val enrolment = gen[TaxEnrolmentsSubscription]

    stubFor(
      get(urlPathEqualTo(s"""/tax-enrolments/subscriptions/$subscriptionId"""))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(Json.toJson(enrolment).toString())
        )
    )

    val response = TaxTestConnector.getSubscription(subscriptionId)
    whenReady(response) { res =>
      res mustEqual enrolment
    }
  }

  "create a new subscription for a tax enrolment" in {
    val safeId = gen[ShortString].value
    val formBundleNumber = gen[ShortString].value

    stubFor(
      put(urlPathEqualTo(s"/tax-enrolments/subscriptions/$formBundleNumber/subscriber"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""""")
        )
    )

    val response = TaxTestConnector.subscribe(safeId, formBundleNumber)
    whenReady(response) { res =>
     res.status mustEqual Status.OK
    }
  }

  "handle an unauthorised exception" in {
    val safeId = gen[ShortString].value
    val formBundleNumber = gen[ShortString].value

    stubFor(
      put(urlPathEqualTo(s"/tax-enrolments/subscriptions/$formBundleNumber/subscriber"))
        .willReturn(
          aResponse()
            .withStatus(Status.UNAUTHORIZED)
        )
    )

    val response = TaxTestConnector.subscribe(safeId, formBundleNumber)
    whenReady(response) { res =>
      res.status mustEqual Status.UNAUTHORIZED
    }
  }

  "handle a BadRequest exception" in {
    val safeId = gen[ShortString].value
    val formBundleNumber = gen[ShortString].value

    stubFor(
      put(urlPathEqualTo(s"/tax-enrolments/subscriptions/$formBundleNumber/subscriber"))
        .willReturn(
          aResponse()
            .withStatus(Status.BAD_REQUEST)
        )
    )

    val response = TaxTestConnector.subscribe(safeId, formBundleNumber)
    whenReady(response) { res =>
      res.status mustEqual Status.BAD_REQUEST
    }
  }

  "should retrieve a DSTRegNumber" in {
    val req = TaxEnrolmentsSubscription(
      Some(
        List(Identifier("DstRefNo", DSTRegNumber("ASDST1010101010")))
      ),
      "etmpId",
      "state",
      None
    ).getDSTNumber

    req mustBe Some(DSTRegNumber("ASDST1010101010"))
  }
}