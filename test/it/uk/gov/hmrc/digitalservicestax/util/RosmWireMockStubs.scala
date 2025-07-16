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

package it.uk.gov.hmrc.digitalservicestax.util

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalToJson, post, stubFor, urlEqualTo, urlPathEqualTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.digitalservicestax.data.{AddressLine, Company, CompanyName, CompanyRegWrapper, Postcode, SafeId, UTR, UkAddress}
import uk.gov.hmrc.digitalservicestax.data.BackendAndFrontendJson.companyRegWrapperFormat

trait RosmWireMockStubs {

  def authPostAuthoriseSuccess(): StubMapping = {
    val jsonResponse =
      s"""
         |{
         |  "allEnrolments": [
         |    {
         |      "key": "IR-CT",
         |      "identifiers": [
         |        {
         |          "key": "UTR",
         |          "value": "1111118888"
         |        }
         |      ],
         |      "state": "Activated",
         |      "confidenceLevel": 50
         |    },
         |    {
         |      "key": "HMRC-NI",
         |      "identifiers": [
         |        {
         |          "key": "NINO",
         |          "value": "GL694656D"
         |        }
         |      ],
         |      "state": "Activated",
         |      "confidenceLevel": 600
         |    }
         |  ]
         |}
         |""".stripMargin

    stubFor(
      post(urlEqualTo(s"""/auth/authorise"""))
        .willReturn(aResponse().withStatus(200).withBody(jsonResponse))
    )
  }

  def stubROSMDetailsSuccess(): StubMapping = {
    val jsonResponse: JsValue = Json.parse("""|{
         |  "safeId": "XE0001234567890",
         |  "sapNumber": "1234567890",
         |  "agentReferenceNumber": "AARN1234567",
         |  "isEditable": true,
         |  "isAnAgent": false,
         |  "isAnASAgent":false,
         |  "isAnIndividual": true,
         |  "organisation": {
         |    "organisationName": "Trotters Trading",
         |    "isAGroup": true,
         |    "organisationType": "Not Specified"
         |  },
         |  "address": {
         |    "addressLine1": "100 SuttonStreet",
         |    "addressLine2": "Wokingham",
         |    "addressLine3": "Surrey",
         |    "addressLine4": "London",
         |    "postalCode": "DH14 1EJ",
         |    "countryCode": "GB"
         |  },
         |  "contactDetails": {
         |    "primaryPhoneNumber": "01332752856",
         |    "secondaryPhoneNumber": "07782565326",
         |    "faxNumber": "01332754256",
         |    "emailAddress": "stephen@manncorpone.co.uk"
         |  }
         |}
         |""".stripMargin)

    val expectedRequestBody =
      s"""
         |{
         |  "regime": "DST",
         |  "requiresNameMatch": false,
         |  "isAnAgent": false
         |}
         |""".stripMargin

    stubFor(
      post(urlEqualTo(s"""/registration/organisation/utr/1111118888"""))
        .withRequestBody(equalToJson(expectedRequestBody))
        .willReturn(aResponse().withStatus(200).withBody(jsonResponse.toString()))
    )
  }

  def stubROSMDetailsNotFound(): StubMapping = {
    val expectedRequestBody =
      s"""
         |{
         |  "regime": "DST",
         |  "requiresNameMatch": false,
         |  "isAnAgent": false
         |}
         |""".stripMargin

    stubFor(
      post(urlEqualTo(s"""/registration/organisation/utr/1111118888"""))
        .withRequestBody(equalToJson(expectedRequestBody))
        .willReturn(aResponse().withStatus(404))
    )
  }
}
