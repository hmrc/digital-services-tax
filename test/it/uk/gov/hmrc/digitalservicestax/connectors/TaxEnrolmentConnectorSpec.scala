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

package it.uk.gov.hmrc.digitalservicestax.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.digitalservicestax.connectors.{Identifier, TaxEnrolmentConnector, TaxEnrolmentsSubscription}
import uk.gov.hmrc.digitalservicestax.data.{AddressLine, CountryCode, DSTRegNumber, ForeignAddress, Postcode, UkAddress}
import uk.gov.hmrc.http.HeaderCarrier
import it.uk.gov.hmrc.digitalservicestax.util.{FakeApplicationSetup, TestInstances, WiremockServer}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

class TaxEnrolmentConnectorSpec extends FakeApplicationSetup with WiremockServer with ScalaCheckDrivenPropertyChecks {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.tax-enrolments.port" -> WireMockSupport.port
    )
    .build()

  object TaxTestConnector
      extends TaxEnrolmentConnector(
        httpClient,
        environment.mode,
        appConfig,
        testConnector
      )

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "should retrieve the latest DST period for a DSTRegNumber" in {
    val subscriptionId = TestInstances.shortString.sample.value
    val enrolment: TaxEnrolmentsSubscription = TaxEnrolmentsSubscription(None, "state", None)

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

  "should return DSTRegNumber as None when identifiers is 'None'" in {
    val enrolment: TaxEnrolmentsSubscription = TaxEnrolmentsSubscription(None, "state", None)
    enrolment.getDSTNumber mustBe None
  }

  "create a new subscription for a tax enrolment" in {
    val safeId           = TestInstances.shortString.sample.value
    val formBundleNumber = TestInstances.shortString.sample.value

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
      res.status mustEqual Status.OK
    }
  }

  "getPendingSubscriptionByGroupId" should {
    "retrieve the tax enrolment with pending state for a groupId" in {
      val groupId            = TestInstances.shortString.sample.value
      val enrolment: JsValue = Json.parse(
        """[{"created":1676542914173,
          "serviceName":"HMRC-DST-ORG",
          "identifiers":[{"key":"DSTRefNumber","value":"XZDST0000000694"}],
          "state":"PENDING","etmpId":"XS0000100406365","groupIdentifier":"5551C230-68B2-4D12-A67F-6C1B6A74D53A"}]""".stripMargin
      )

      stubFor(
        get(urlPathEqualTo(s"""/tax-enrolments/groups/$groupId/subscriptions"""))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(enrolment.toString())
          )
      )
      val expectedResult =
        Some(TaxEnrolmentsSubscription(Some(List(Identifier("DSTRefNumber", "XZDST0000000694"))), "PENDING", None))

      val response = TaxTestConnector.getPendingSubscriptionByGroupId(groupId)
      whenReady(response) { res =>
        res mustBe expectedResult
      }
    }
    "retrieve the taxenrolment by groupId and return none when state is not pending" in {
      val groupId            = TestInstances.shortString.sample.value
      val enrolment: JsValue = Json.parse(
        """[{"created":1676542914173,
          "serviceName":"HMRC-DST-ORG",
          "identifiers": null,
          "state":"OFFLINE","etmpId":"XS0000100406365","groupIdentifier":"5551C230-68B2-4D12-A67F-6C1B6A74D53A"}]""".stripMargin
      )

      stubFor(
        get(urlPathEqualTo(s"""/tax-enrolments/groups/$groupId/subscriptions"""))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(enrolment.toString())
          )
      )

      val response = TaxTestConnector.getPendingSubscriptionByGroupId(groupId)
      whenReady(response) { res =>
        res mustBe None
      }
    }
  }

  "isAllocateDstGroupEnrolmentSuccess" should {
    "return true when allocate group enrolment is successful and returns 204 with Postcode as verifier" in {
      val requestBody: String = Json
        .parse(s"""
               |{
               |    "identifiers": [
               |        {
               |            "key": "DSTRefNumber",
               |            "value": "1234567890"
               |        }
               |    ],
               |    "verifiers": [
               |        {
               |            "key": "Postcode",
               |            "value": "AA1 2BB"
               |        }
               |    ]
               |}
         """.stripMargin)
        .toString()

      stubFor(
        put(urlPathEqualTo("""/tax-enrolments/service/HMRC-DST-ORG/enrolment"""))
          .withRequestBody(equalToJson(requestBody))
          .willReturn(
            aResponse()
              .withStatus(204)
          )
      )

      val response = TaxTestConnector.isAllocateDstGroupEnrolmentSuccess(
        UkAddress(AddressLine("address line 1"), None, None, None, Postcode("AA1 2BB")),
        "1234567890"
      )
      whenReady(response) { res =>
        res mustBe true
      }
    }
    "return true when allocate group enrolment is successful and returns 204 with NonUkCountryCode" in {
      val requestBody: String = Json
        .parse(s"""
                  |{
                  |    "identifiers": [
                  |        {
                  |            "key": "DSTRefNumber",
                  |            "value": "1234567890"
                  |        }
                  |    ],
                  |    "verifiers": [
                  |        {
                  |            "key": "NonUkCountryCode",
                  |            "value": "IN"
                  |        }
                  |    ]
                  |}
         """.stripMargin)
        .toString()

      stubFor(
        put(urlPathEqualTo("""/tax-enrolments/service/HMRC-DST-ORG/enrolment"""))
          .withRequestBody(equalToJson(requestBody))
          .willReturn(
            aResponse()
              .withStatus(204)
          )
      )

      val response = TaxTestConnector.isAllocateDstGroupEnrolmentSuccess(
        ForeignAddress(AddressLine("address line 1"), None, None, None, CountryCode("IN")),
        "1234567890"
      )
      whenReady(response) { res =>
        res mustBe true
      }
    }
    "return false when allocate group enrolment fails and returns 400 with Postcode as verifier" in {

      stubFor(
        put(urlPathEqualTo("""/tax-enrolments/service/HMRC-DST-ORG/enrolment"""))
          .willReturn(
            aResponse()
              .withStatus(400)
              .withBody("Provided service name is not in services-to-activate")
          )
      )

      val response = TaxTestConnector.isAllocateDstGroupEnrolmentSuccess(
        UkAddress(AddressLine("address line 1"), None, None, None, Postcode("AA1 2CC")),
        "1234567890"
      )
      whenReady(response) { res =>
        res mustBe false
      }
    }
    "return false when allocate group enrolment fails and returns 400 with NonUkCountryCode as verifier" in {

      stubFor(
        put(urlPathEqualTo("""/tax-enrolments/service/HMRC-DST-ORG/enrolment"""))
          .willReturn(
            aResponse()
              .withStatus(400)
              .withBody("Provided service name is not in services-to-activate")
          )
      )

      val response = TaxTestConnector.isAllocateDstGroupEnrolmentSuccess(
        ForeignAddress(AddressLine("address line 1"), None, None, None, CountryCode("IN")),
        "1234567890"
      )
      whenReady(response) { res =>
        res mustBe false
      }
    }
  }

  "handle an unauthorised exception" in {
    val safeId           = TestInstances.shortString.sample.value
    val formBundleNumber = TestInstances.shortString.sample.value

    stubFor(
      put(urlPathEqualTo(s"/tax-enrolments/subscriptions/$formBundleNumber/subscriber"))
        .willReturn(
          aResponse()
            .withStatus(Status.UNAUTHORIZED)
        )
    )

    val response = TaxTestConnector.subscribe(safeId, formBundleNumber)
    whenReady(response) { res =>
      res.status mustEqual Status.UNAUTHORIZED
    }
  }

  "handle a BadRequest exception" in {
    val safeId           = TestInstances.shortString.sample.value
    val formBundleNumber = TestInstances.shortString.sample.value

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

  "should retrieve a DSTRegNumber" in {
    val req = TaxEnrolmentsSubscription(
      Some(
        List(Identifier("DstRefNo", DSTRegNumber("ASDST1010101010")))
      ),
      "state",
      None
    ).getDSTNumber

    req mustBe Some(DSTRegNumber("ASDST1010101010"))
  }
}
