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

package it.uk.gov.hmrc.digitalservicestax.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import com.outworkers.util.domain.ShortString
import com.outworkers.util.samplers._
import org.scalacheck.Arbitrary.arbitrary
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.Json
import uk.gov.hmrc.digitalservicestax.backend_data.ReturnResponse
import uk.gov.hmrc.digitalservicestax.connectors.ReturnConnector
import uk.gov.hmrc.digitalservicestax.data.BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.data.{DSTRegNumber, Period, Return}
import uk.gov.hmrc.http.HeaderCarrier
import it.uk.gov.hmrc.digitalservicestax.util.TestInstances._
import it.uk.gov.hmrc.digitalservicestax.util.{FakeApplicationSetup, WiremockServer}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.digitalservicestax.data.Period.Key

import java.time.LocalDate

class ReturnConnectorSpec extends FakeApplicationSetup with WiremockServer with ScalaCheckDrivenPropertyChecks {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.des.port" -> WireMockSupport.port
    )
    .build()

  object ReturnTestConnector extends ReturnConnector(httpClient, environment.mode, appConfig)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "should retrieve the latest DST period json for a DSTRegNumber" in {
    val dstRegNumber = arbitrary[DSTRegNumber].sample.value
    val periods      = s"""{
                          |   "obligations":[
                          |      {
                          |         "obligationDetails":[
                          |            {
                          |               "status":"O",
                          |               "inboundCorrespondenceFromDate":"2023-09-27",
                          |               "inboundCorrespondenceToDate":"2024-09-26",
                          |               "inboundCorrespondenceDueDate":"2025-09-26",
                          |               "periodKey":"#005"
                          |            },
                          |            {
                          |               "status":"O",
                          |               "inboundCorrespondenceFromDate":"2022-09-27",
                          |               "inboundCorrespondenceToDate":"2023-09-26",
                          |               "inboundCorrespondenceDueDate":"2024-09-26",
                          |               "periodKey":"#004"
                          |            },
                          |            {
                          |               "status":"O",
                          |               "inboundCorrespondenceFromDate":"2021-09-27",
                          |               "inboundCorrespondenceToDate":"2022-09-26",
                          |               "inboundCorrespondenceDueDate":"2023-09-26",
                          |               "periodKey":"#003"
                          |            },
                          |            {
                          |               "status":"F",
                          |               "inboundCorrespondenceFromDate":"2020-09-27",
                          |               "inboundCorrespondenceToDate":"2021-09-26",
                          |               "inboundCorrespondenceDateReceived":"2022-06-20",
                          |               "inboundCorrespondenceDueDate":"2022-09-26",
                          |               "periodKey":"#002"
                          |            },
                          |            {
                          |               "status":"F",
                          |               "inboundCorrespondenceFromDate":"2020-04-01",
                          |               "inboundCorrespondenceToDate":"2020-09-26",
                          |               "inboundCorrespondenceDateReceived":"2021-06-22",
                          |               "inboundCorrespondenceDueDate":"2021-09-26",
                          |               "periodKey":"#001"
                          |            }
                          |         ]
                          |      }
                          |   ]
                          |}""".stripMargin

    val expectedResponse = List(
      (
        Period(LocalDate.of(2023, 9, 27), LocalDate.of(2024, 9, 26), LocalDate.of(2025, 9, 26), Key("005")),
        None
      ),
      (
        Period(LocalDate.of(2022, 9, 27), LocalDate.of(2023, 9, 26), LocalDate.of(2024, 9, 26), Key("004")),
        None
      ),
      (
        Period(LocalDate.of(2021, 9, 27), LocalDate.of(2022, 9, 26), LocalDate.of(2023, 9, 26), Key("003")),
        None
      ),
      (
        Period(
          LocalDate.of(2020, 9, 27),
          LocalDate.of(2021, 9, 26),
          LocalDate.of(2022, 9, 26),
          Key("002")
        ),
        Some(LocalDate.of(2022, 6, 20))
      ),
      (
        Period(LocalDate.of(2020, 4, 1), LocalDate.of(2020, 9, 26), LocalDate.of(2021, 9, 26), Key("001")),
        Some(LocalDate.of(2021, 6, 22))
      )
    )

    stubFor(
      get(urlPathEqualTo(s"""/enterprise/obligation-data/zdst/$dstRegNumber/DST"""))
        .willReturn(aResponse().withStatus(200).withBody(periods))
    )

    val response = ReturnTestConnector.getPeriods(dstRegNumber)
    whenReady(response) { res =>
      res mustEqual expectedResponse
    }
  }

  "should retrieve the latest DST period for a DSTRegNumber" in {
    val dstRegNumber = arbitrary[DSTRegNumber].sample.value

    stubFor(
      get(urlPathEqualTo(s"""/enterprise/obligation-data/zdst/$dstRegNumber/DST"""))
        .willReturn(aResponse().withStatus(200))
    )

    val response = ReturnTestConnector.getNextPendingPeriod(dstRegNumber)
    whenReady(response.failed) { res =>
      res
    }
  }

  "should retrieve the a list of DST periods for a DSTRegNumber" in {
    val dstRegNumber = arbitrary[DSTRegNumber].sample.value
    val periods      = arbitrary[List[Period]].sample.value.map(_ -> Option.empty[LocalDate])

    stubFor(
      get(urlPathEqualTo(s"""/enterprise/obligation-data/zdst/$dstRegNumber/DST"""))
        .willReturn(aResponse().withStatus(200).withBody(Json.toJson(periods).toString()))
    )

    val response = ReturnTestConnector.getPeriods(dstRegNumber)
    whenReady(response.failed) { res =>
      res
    }
  }

  "should send a new period/DST number" in {
    val customGen = for {
      dstRegNumber <- arbitrary[DSTRegNumber]
      period       <- arbitrary[Period]
      ret          <- arbitrary[Return]
    } yield (dstRegNumber, period, ret)

    val resp = ReturnResponse(
      gen[ShortString].value,
      gen[ShortString].value
    )

    forAll(customGen) { case (dstNo, period, ret) =>
      stubFor(
        post(urlPathEqualTo(s"""/cross-regime/return/DST/zdst/$dstNo"""))
          .willReturn(aResponse().withStatus(200).withBody(Json.toJson(resp).toString()))
      )

      val response = ReturnTestConnector.send(dstNo, period, ret, isAmend = false)
      whenReady(response) { res =>
        res mustEqual resp
      }
    }

  }

}
