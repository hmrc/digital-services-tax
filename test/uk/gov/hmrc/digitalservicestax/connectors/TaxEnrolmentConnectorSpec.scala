/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.digitalservicestax.connectors

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, put, stubFor, urlPathEqualTo}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.digitalservicestax.util.WiremockSpec
import com.outworkers.util.samplers._
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.audit.handler.HttpResult.Response
import uk.gov.hmrc.http.HeaderCarrier

class TaxEnrolmentConnectorSpec extends WiremockSpec with ScalaCheckDrivenPropertyChecks {

  object TaxTestConnector extends TaxEnrolmentConnector(
    httpClient,
    environment.mode,
    servicesConfig,
    appConfig,
    testConnector
  ) {
    override lazy val taxEnrolmentsUrl: String = mockServerUrl
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "should correctly configure a tax enrolments URL" in {
    TaxTestConnector.taxEnrolmentsUrl.isEmpty mustEqual false
  }

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
    whenReady(response.failed) { res =>
      res
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
}