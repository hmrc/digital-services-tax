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
import uk.gov.hmrc.digitalservicestax.data.{Company, CompanyRegWrapper, ContactDetails}
import uk.gov.hmrc.digitalservicestax.util.WiremockSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.digitalservicestax.util.TestInstances._
import org.scalacheck.Arbitrary.arbitrary
import com.outworkers.util.samplers._

class RosmConnectorSpec extends WiremockSpec with ScalaCheckDrivenPropertyChecks {

  object RosmTestConnector extends RosmConnector(httpClient, environment.mode, servicesConfig, appConfig) {
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
          .withBody(Json.toJson(req).toString())
          .withStatus(500)))

    val response = RosmTestConnector.retrieveROSMDetails("1234567890")
    whenReady(response.failed) { res =>
      res
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
        .willReturn(aResponse()
        .withBody(Json.toJson(req).toString())
        .withStatus(429)))

    whenReady(RosmTestConnector.retrieveROSMDetails("1234567890").failed) { ex =>
      Console.println(ex.getMessage)
    }
  }

  "should get a response back if des available" ignore {
    val resp = arbitrary[CompanyRegWrapper].sample.value

    stubFor(
      post(urlPathEqualTo("/registration/organisation/utr/1234567890"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              /// Json.toJson(resp).toString()
              """"""
            )
        )
    )

    val future = RosmTestConnector.retrieveROSMDetails("1234567890")
    whenReady(future) { x =>
      x mustBe empty
    }
  }


 "retrieve ROSM details without ID" in {

   import RosmTestConnector._
   import uk.gov.hmrc.digitalservicestax.backend_data.RosmFormats._

   val req = arbitrary[RosmRegisterWithoutIDRequest].sample.value
   val response = gen[RosmWithoutIDResponse]

   stubFor(
      post(urlPathEqualTo(s"$desURL/$serviceURLWithoutId"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              ///Json.toJson(response).toString()
              """"""
            )
        )
   )

    val future = RosmTestConnector.retrieveROSMDetailsWithoutID(req)
    whenReady(future) { x =>
      x mustBe empty
    }
  }
}
