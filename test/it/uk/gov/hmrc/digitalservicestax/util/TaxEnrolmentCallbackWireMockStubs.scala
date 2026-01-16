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

package it.uk.gov.hmrc.digitalservicestax.util

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, put, stubFor, urlEqualTo, urlPathEqualTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.json.Json
import uk.gov.hmrc.digitalservicestax.connectors.{Identifier, TaxEnrolmentsSubscription}
import uk.gov.hmrc.digitalservicestax.data.{DSTRegNumber, FormBundleNumber}

import java.time.LocalDate

trait TaxEnrolmentCallbackWireMockStubs {

  def stubTaxEnrolmentsSubscribeEndpointSuccess(
    formBundleNumber: FormBundleNumber,
    dstRegNumber: DSTRegNumber
  ): StubMapping = {
    val enrolmentSubscription =
      TaxEnrolmentsSubscription(Some(Seq(Identifier("DST", dstRegNumber))), "SUCCEEDED", None)
    stubFor(
      WireMock
        .get(urlPathEqualTo(s"""/tax-enrolments/subscriptions/$formBundleNumber"""))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(Json.toJson(enrolmentSubscription).toString())
        )
    )
  }

  def stubGetPeriodsSuccess(dstRegNo: DSTRegNumber): StubMapping = {
    val jsonResponse =
      """
        |{
        |	"obligations": [
        |		{
        |			"identification": {
        |				"incomeSourceType": "ITSA",
        |				"referenceNumber": "AB123456A",
        |				"referenceType": "NINO"
        |			},
        |			"obligationDetails": [
        |				{
        |					"status": "O",
        |					"inboundCorrespondenceFromDate": "2021-02-02",
        |					"inboundCorrespondenceToDate": "2021-03-03",
        |					"inboundCorrespondenceDueDate": "2021-02-02",
        |					"periodKey": "001"
        |				},
        |				{
        |					"status": "O",
        |					"inboundCorrespondenceFromDate": "2023-02-02",
        |					"inboundCorrespondenceToDate": "2024-03-03",
        |					"inboundCorrespondenceDateReceived": "2021-02-02",
        |					"inboundCorrespondenceDueDate": "2021-02-02",
        |					"periodKey": "001"
        |				}
        |			]
        |		}
        |	]
        |}
        |
        |""".stripMargin

    stubFor(
      get(urlEqualTo(s"""/enterprise/obligation-data/zdst/$dstRegNo/DST?from=2020-04-01&to=${LocalDate.now.plusYears(
          1
        )}"""))
        .willReturn(aResponse().withStatus(200).withBody(jsonResponse))
    )
  }

  def stubGetPeriodsError(dstRegNo: DSTRegNumber): StubMapping = {
    val jsonResponse =
      """
        |{
        |	"obligations": [
        |		{
        |			"identification": {
        |				"incomeSourceType": "ITSA",
        |				"referenceNumber": "AB123456A",
        |				"referenceType": "NINO"
        |			},
        |			"obligationDetails": [
        |				{
        |					"status": "O",
        |					"inboundCorrespondenceFromDate": "2021-02-02",
        |					"inboundCorrespondenceToDate": "2021-03-03",
        |					"inboundCorrespondenceDateReceived": "2021-02-02",
        |					"inboundCorrespondenceDueDate": "2021-02-02",
        |					"periodKey": "001"
        |				},
        |				{
        |					"status": "O",
        |					"inboundCorrespondenceFromDate": "2023-02-02",
        |					"inboundCorrespondenceToDate": "2024-03-03",
        |					"inboundCorrespondenceDateReceived": "2021-02-02",
        |					"inboundCorrespondenceDueDate": "2021-02-02",
        |					"periodKey": "001"
        |				}
        |			]
        |		}
        |	]
        |}
        |
        |""".stripMargin

    stubFor(
      get(urlEqualTo(s"""/enterprise/obligation-data/zdst/$dstRegNo/DST?from=2020-04-01&to=${LocalDate.now.plusYears(
          1
        )}"""))
        .willReturn(aResponse().withStatus(200).withBody(jsonResponse))
    )
  }
}
