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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, stubFor, urlPathEqualTo}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.Json
import uk.gov.hmrc.digitalservicestax.backend_data.{RosmRegisterWithoutIDRequest, RosmWithoutIDResponse}
import uk.gov.hmrc.digitalservicestax.data.{Company, ContactDetails}
import uk.gov.hmrc.digitalservicestax.util.WiremockSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import uk.gov.hmrc.digitalservicestax.util.TestInstances._
import org.scalacheck.Arbitrary.arbitrary

class RosmConnectorSpec extends WiremockSpec with ScalaCheckDrivenPropertyChecks {

  object TestConnector extends RosmConnector(httpClient, environment.mode, servicesConfig) {
    override val desURL: String = mockServerUrl
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "should get no response back if des is not available" in {
    val req = RosmRegisterWithoutIDRequest(
      isAnAgent = false,
      isAGroup = false,
      arbitrary[Company].sample.value,
      arbitrary[ContactDetails].sample.value
    )

    stubFor(
      post(urlPathEqualTo("/registration/organisation/utr/1234567890"))
        .willReturn(aResponse()
          .withStatus(500)))

    val response = TestConnector.retrieveROSMDetails("1234567890")
    whenReady(response) { x =>
      x mustBe None
    }
  }

  "should get an upstream5xx response if des is returning 429" in {
    val req = RosmRegisterWithoutIDRequest(
      isAnAgent = false,
      isAGroup = false,
      arbitrary[Company].sample.value,
      arbitrary[ContactDetails].sample.value
    )

    stubFor(
      post(urlPathEqualTo("/registration/organisation/utr/1234567890"))
        .willReturn(aResponse().withStatus(429)))

    val ex = the[Exception] thrownBy (TestConnector.retrieveROSMDetails("1234567890").futureValue)
    ex.getMessage must startWith("The future returned an exception of type: uk.gov.hmrc.http.Upstream5xxResponse")
  }

  "should get a response back if des available" in {
    val req = RosmRegisterWithoutIDRequest(
      isAnAgent = false,
      isAGroup = false,
      arbitrary[Company].sample.value,
      arbitrary[ContactDetails].sample.value
    )

    stubFor(
      post(urlPathEqualTo("/registration/organisation/utr/1234567890"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(Json.toJson(req).toString())))

    val future = TestConnector.retrieveROSMDetails("1234567890")
    whenReady(future) { x =>
      x mustBe defined
    }
  }
}
