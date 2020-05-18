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
import org.scalacheck.Arbitrary.arbitrary
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.digitalservicestax.data.{FormBundleNumber, Registration}
import uk.gov.hmrc.digitalservicestax.util.WiremockSpec
import uk.gov.hmrc.digitalservicestax.util.TestInstances._
import com.outworkers.util.samplers._
import play.api.libs.json.Json
import uk.gov.hmrc.digitalservicestax.backend_data.RegistrationResponse
import uk.gov.hmrc.http.HeaderCarrier

class RegistrationConnectorSpec extends WiremockSpec with ScalaCheckDrivenPropertyChecks {

  object RegTestConnector extends RegistrationConnector(httpClient, environment.mode, servicesConfig, appConfig, implicitly) {
    override val desURL: String = mockServerUrl
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "should retrieve the a list of DST periods for a DSTRegNumber" in {

    val resp = RegistrationResponse(
      gen[ShortString].value,
      arbitrary[FormBundleNumber].sample.value
    )

    val idType = gen[ShortString].value
    val idNumber = gen[ShortString].value
    val reg = arbitrary[Registration].sample.value

    stubFor(
      post(urlPathEqualTo(s"""/cross-regime/subscription/DST/$idType/$idNumber"""))
        .willReturn(aResponse().withStatus(200).withBody(Json.toJson(resp).toString())))


    val response = RegTestConnector.send(idType, Some(idNumber), reg)
    whenReady(response) { res =>
      res
    }
  }

  "should throw an error if no FormBundleNumber id Number" in {
    val idType = gen[ShortString].value
    val reg = arbitrary[Registration].sample.value

    whenReady(RegTestConnector.send(idType, None, reg).failed) { ex =>
      ex.getMessage mustEqual s"Missing idNumber for idType: $idType"
    }

  }

}
