/*
 * Copyright 2023 HM Revenue & Customs
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
import com.outworkers.util.samplers._
import org.scalacheck.Arbitrary.arbitrary
import org.scalatestplus.mockito._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.Json
import uk.gov.hmrc.digitalservicestax.backend_data.RegistrationResponse
import uk.gov.hmrc.digitalservicestax.connectors.RegistrationConnector
import uk.gov.hmrc.digitalservicestax.data.{FormBundleNumber, Registration}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import it.uk.gov.hmrc.digitalservicestax.util.TestInstances._
import it.uk.gov.hmrc.digitalservicestax.util.{FakeApplicationSetup, WiremockServer}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

class RegistrationConnectorSpec
    extends FakeApplicationSetup
    with WiremockServer
    with ScalaCheckDrivenPropertyChecks
    with MockitoSugar {

  val auditing: AuditConnector = mock[AuditConnector]

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.des.port" -> WireMockSupport.port
    )
    .build()

  object RegTestConnector extends RegistrationConnector(httpClient, environment.mode, appConfig, auditing)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "should retrieve the a list of DST periods for a DSTRegNumber" in {

    val resp = RegistrationResponse(
      gen[ShortString].value,
      arbitrary[FormBundleNumber].sample.value
    )

    val idType   = gen[ShortString].value
    val idNumber = gen[ShortString].value
    val reg      = arbitrary[Registration].sample.value

    stubFor(
      post(urlPathEqualTo(s"""/cross-regime/subscription/DST/$idType/$idNumber"""))
        .willReturn(aResponse().withStatus(200).withBody(Json.toJson(resp).toString()))
    )

    val response = RegTestConnector.send(idType, idNumber, reg)
    whenReady(response) { res =>
      res
    }
  }

}
