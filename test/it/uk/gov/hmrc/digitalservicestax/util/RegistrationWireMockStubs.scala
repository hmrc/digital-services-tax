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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.json.Json
import uk.gov.hmrc.digitalservicestax.backend_data.RosmFormats._
import uk.gov.hmrc.digitalservicestax.backend_data.{RegistrationResponse, RosmWithoutIDResponse}
import uk.gov.hmrc.digitalservicestax.connectors.{Identifier, TaxEnrolmentsSubscription}
import uk.gov.hmrc.digitalservicestax.data.{DSTRegNumber, EnrolmentDetail, FormBundleNumber, GroupEnrolmentsResponse, SafeId, ServiceIdentifier, UTR}

import java.time.LocalDate

trait RegistrationWireMockStubs {

  def stubDesRegEndpointUsingSafeIdSuccess(
    safeId: SafeId,
    formBundleNumber: FormBundleNumber,
    regResponse: Option[RegistrationResponse]
  ): StubMapping = {
    val desRegistrationResponse = regResponse.getOrElse(RegistrationResponse(LocalDate.now.toString, formBundleNumber))
    stubFor(
      post(urlPathEqualTo(s"""/cross-regime/subscription/DST/safe/$safeId"""))
        .willReturn(aResponse().withStatus(200).withBody(Json.toJson(desRegistrationResponse).toString()))
    )
  }

  def stubDesRegEndpointUsingUTRSuccess(
    utr: UTR,
    formBundleNumber: FormBundleNumber,
    regResponse: Option[RegistrationResponse]
  ): StubMapping = {
    val desRegistrationResponse = regResponse.getOrElse(RegistrationResponse(LocalDate.now.toString, formBundleNumber))
    stubFor(
      post(urlPathEqualTo(s"""/cross-regime/subscription/DST/utr/$utr"""))
        .willReturn(aResponse().withStatus(200).withBody(Json.toJson(desRegistrationResponse).toString()))
    )
  }

  def stubDesRegEndpointError(safeId: SafeId): StubMapping =
    stubFor(
      post(urlPathEqualTo(s"""/cross-regime/subscription/DST/safe/$safeId"""))
        .willReturn(aResponse().withStatus(500))
    )

  def stubRetrieveROSMDetailsWithoutIDEndpointSuccess(safeId: SafeId): StubMapping =
    stubFor(
      post(urlPathEqualTo(s"/registration/02.00.00/organisation"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              Json.toJson(RosmWithoutIDResponse(LocalDate.now.minusYears(1).toString, "123", safeId, None)).toString
            )
        )
    )

  def stubTaxEnrolmentsSubscribeEndpointSuccess(formBundleNumber: FormBundleNumber): StubMapping = {
    val enrolmentSubscription =
      TaxEnrolmentsSubscription(Some(Seq(Identifier("DST", "AMDST0799721562"))), "SUCCEEDED", None)
    stubFor(
      put(urlPathEqualTo(s"""/tax-enrolments/subscriptions/$formBundleNumber/subscriber"""))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(Json.toJson(enrolmentSubscription).toString())
        )
    )
  }

  def stubTaxEnrolmentsSubscribeEndpointError(formBundleNumber: FormBundleNumber): StubMapping =
    stubFor(
      put(urlPathEqualTo(s"""/tax-enrolments/subscriptions/$formBundleNumber/subscriber"""))
        .willReturn(aResponse().withStatus(500))
    )

  def stubTaxEnrolmentsStoreProxyDstRefSuccess(groupId: String): StubMapping = {
    val enrolmentResponse = GroupEnrolmentsResponse(
      List(EnrolmentDetail("HMRC-DST-ORG", Seq(ServiceIdentifier("DSTRefNumber", "AMDST0799721562")), "Activated"))
    )
    stubFor(
      get(urlEqualTo(s"""/enrolment-store-proxy/enrolment-store/groups/$groupId/enrolments?service=HMRC-DST-ORG"""))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(Json.toJson(enrolmentResponse).toString())
        )
    )
  }

  def stubTaxEnrolmentsStoreProxyDstRefNotFound(groupId: String): StubMapping =
    stubFor(
      get(urlEqualTo(s"/enrolment-store-proxy/enrolment-store/groups/$groupId/enrolments?service=HMRC-DST-ORG"))
        .willReturn(notFound())
    )

  def stubTaxEnrolmentsPendingSubscriptionsSuccess(
    groupId: String,
    dstRegNo: DSTRegNumber,
    state: String,
    errorResponse: Option[String] = None
  ): StubMapping = {
    val subscriptionResponse = Seq(
      TaxEnrolmentsSubscription(Some(List(Identifier("DST", dstRegNo))), state, errorResponse)
    )

    stubFor(
      get(urlEqualTo(s"""/tax-enrolments/groups/$groupId/subscriptions"""))
        .willReturn(aResponse().withStatus(200).withBody(Json.toJson(subscriptionResponse).toString()))
    )
  }
}
