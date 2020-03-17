/*
 * Copyright 2020 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlPathEqualTo}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.digitalservicestax.data.DSTRegNumber
import uk.gov.hmrc.digitalservicestax.util.WiremockSpec
import com.outworkers.util.samplers._
import play.api.libs.json.Json
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

  "should retrieve the latest DST period for a DSTRegNumber" ignore {
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


}