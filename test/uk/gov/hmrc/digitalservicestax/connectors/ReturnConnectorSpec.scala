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
import org.scalacheck.Arbitrary.arbitrary
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.digitalservicestax.data.DSTRegNumber
import uk.gov.hmrc.digitalservicestax.util.TestInstances._
import uk.gov.hmrc.digitalservicestax.util.WiremockSpec
import uk.gov.hmrc.http.HeaderCarrier

class ReturnConnectorSpec extends WiremockSpec with ScalaCheckDrivenPropertyChecks {

  object ReturnTestConnector extends ReturnConnector(httpClient, environment.mode, servicesConfig, appConfig) {
    override val desURL: String = mockServerUrl
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "should retrieve the latest DST period for a DSTRegNumber" in {
    val dstRegNumber = arbitrary[DSTRegNumber].sample.value

    stubFor(
      get(urlPathEqualTo(s"""/enterprise/obligation-data/zdst/$dstRegNumber/DST"""))
        .willReturn(aResponse().withStatus(200)))

    val response = ReturnTestConnector.getNextPendingPeriod(dstRegNumber)
    whenReady(response.failed) { res =>
      res
    }
  }

  "should retrieve the a list of DST periods for a DSTRegNumber" in {
    val dstRegNumber = arbitrary[DSTRegNumber].sample.value

    stubFor(
      get(urlPathEqualTo(s"""/enterprise/obligation-data/zdst/$dstRegNumber/DST"""))
        .willReturn(aResponse().withStatus(200)))

    val response = ReturnTestConnector.getPeriods(dstRegNumber)
    whenReady(response.failed) { res =>
      res
    }
  }

}