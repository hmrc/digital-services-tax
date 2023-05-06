///*
// * Copyright 2023 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package it.uk.gov.hmrc.digitalservicestax.connectors
//
//import com.github.tomakehurst.wiremock.client.WireMock._
//import it.uk.gov.hmrc.digitalservicestax.util.{FakeApplicationSetup, WiremockServer}
//import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
//import play.api.Application
//import play.api.inject.guice.GuiceApplicationBuilder
//import play.api.test.Helpers.{await, defaultAwaitTimeout}
//import uk.gov.hmrc.digitalservicestax.connectors.EnrolmentStoreProxyConnector
//import uk.gov.hmrc.http.HeaderCarrier
//
//class EnrolmentStoreProxyConnectorSpec
//    extends FakeApplicationSetup
//    with WiremockServer
//    with ScalaCheckDrivenPropertyChecks {
//
//  override implicit lazy val app: Application = new GuiceApplicationBuilder()
//    .configure(
//      "microservice.services.enrolment-store-proxy.port" -> WireMockSupport.port
//    )
//    .build()
//
//  object EspTestConnector
//      extends EnrolmentStoreProxyConnector(
//        httpClient,
//        appConfig
//      )
//
//  implicit val hc: HeaderCarrier = HeaderCarrier()
//
//  "should retrieve the latest DSTRegNumber when we get right 200 response" in {
//    stubFor(
//      get(urlEqualTo("""/groups/12345/enrolments?service=HMRC-DST-ORG"""))
//        .willReturn(
//          aResponse()
//            .withStatus(200)
//            .withBody("""{
//                        |    "startRecord":1,
//                        |    "totalRecords":2,
//                        |    "enrolments":[
//                        |
//                        |                {
//                        |                    "service":"IR-CT",
//                        |                    "state":"NotYetActivated",
//                        |                    "friendlyName":"",
//                        |                     "enrolmentDate":"2023-02-01 13:27:09.299",
//                        |                     "failedActivationCount":0,
//                        |
//                        |                     "enrolmentTokenExpiryDate":"2023-03-03 13:27:09.299",
//                        |                    "identifiers": [
//                        |
//                        |                            {
//                        |                                "key":"UTR",
//                        |                                "value":"1777567666"
//                        |                            }
//                        |
//                        |                    ]
//                        |                }
//                        |        ,
//                        |                {
//                        |                    "service":"HMRC-DST-ORG",
//                        |                    "state":"Activated",
//                        |                    "friendlyName":"",
//                        |                     "enrolmentDate":"2023-05-05 12:19:26.798",
//                        |                     "failedActivationCount":0,
//                        |                     "activationDate":"2023-05-05 12:19:26.798",
//                        |
//                        |                    "identifiers": [
//                        |
//                        |                            {
//                        |                                "key":"DSTRefNumber",
//                        |                                "value":"XYDST0000000745"
//                        |                            }
//                        |
//                        |                    ]
//                        |                }
//                        |
//                        |    ]
//                        |}
//                        |""".stripMargin)
//        )
//    )
//
//    val response = EspTestConnector.getDstRefFromGroupAssignedEnrolment("12345")
//    whenReady(response) { res =>
//      res mustEqual Some("XYDST0000000745")
//    }
//  }
//
//  "should return DSTRegNumber as None when response is 204" in {
//    stubFor(
//      get(urlEqualTo(s"""/groups/12345/enrolments?service=HMRC-DST-ORG"""))
//        .willReturn(
//          aResponse()
//            .withStatus(204)
//        )
//    )
//
//    val response = EspTestConnector.getDstRefFromGroupAssignedEnrolment("12345")
//    whenReady(response) { res =>
//      res mustEqual None
//    }
//  }
//
//  "should return None when we DST ref exists and status is not activated" in {
//
//    stubFor(
//      get(urlEqualTo(s"""/groups/12345/enrolments?service=HMRC-DST-ORG"""))
//        .willReturn(
//          aResponse()
//            .withStatus(200)
//            .withBody("""{
//                        |    "startRecord":1,
//                        |    "totalRecords":2,
//                        |    "enrolments":[
//                        |
//                        |                {
//                        |                    "service":"IR-CT",
//                        |                    "state":"NotYetActivated",
//                        |                    "friendlyName":"",
//                        |                     "enrolmentDate":"2023-02-01 13:27:09.299",
//                        |                     "failedActivationCount":0,
//                        |
//                        |                     "enrolmentTokenExpiryDate":"2023-03-03 13:27:09.299",
//                        |                    "identifiers": [
//                        |
//                        |                            {
//                        |                                "key":"UTR",
//                        |                                "value":"1777567666"
//                        |                            }
//                        |
//                        |                    ]
//                        |                }
//                        |        ,
//                        |                {
//                        |                    "service":"HMRC-DST-ORG",
//                        |                    "state":"NotYetActivated",
//                        |                    "friendlyName":"",
//                        |                     "enrolmentDate":"2023-05-05 12:19:26.798",
//                        |                     "failedActivationCount":0,
//                        |                     "activationDate":"2023-05-05 12:19:26.798",
//                        |
//                        |                    "identifiers": [
//                        |
//                        |                            {
//                        |                                "key":"DSTRefNumber",
//                        |                                "value":"XYDST0000000745"
//                        |                            }
//                        |
//                        |                    ]
//                        |                }
//                        |
//                        |    ]
//                        |}
//                        |""".stripMargin)
//        )
//    )
//
//    val response = EspTestConnector.getDstRefFromGroupAssignedEnrolment("12345")
//    whenReady(response) { res =>
//      res mustEqual None
//    }
//  }
//
//  "should return exception when we response is 200 but invalid json" in {
//
//    stubFor(
//      get(urlEqualTo(s"""/groups/12345/enrolments?service=HMRC-DST-ORG"""))
//        .willReturn(
//          aResponse()
//            .withStatus(200)
//            .withBody("""{
//                        |    "startRecord":1,
//                        |    "totalRecords":2,
//                        |    "enrolments":[
//                        |                {
//                        |                    "friendlyName":"",
//                        |                     "enrolmentDate":"2023-02-01 13:27:09.299",
//                        |                     "failedActivationCount":0,
//                        |
//                        |                     "enrolmentTokenExpiryDate":"2023-03-03 13:27:09.299",
//                        |                    "identifiers": [
//                        |
//                        |                            {
//                        |                                "key":"UTR",
//                        |                                "value":"1777567666"
//                        |                            }
//                        |
//                        |                    ]
//                        |                }
//                        |    ]
//                        |}
//                        |""".stripMargin)
//        )
//    )
//
//    val response = EspTestConnector.getDstRefFromGroupAssignedEnrolment("12345")
//    intercept[Exception](
//      await(response)
//    ).getMessage mustBe "Unexpected exception while getting group enrolments from ESP. Exception: Unexpected Response body from enrolment store proxy"
//  }
//
//  "should return Exception when a non success response is returned" in {
//    stubFor(
//      get(urlEqualTo(s"""/groups/12345/enrolments?service=HMRC-DST-ORG"""))
//        .willReturn(
//          aResponse()
//            .withStatus(400)
//            .withBody("Multiple Errors with enrolments")
//        )
//    )
//
//    val response = EspTestConnector.getDstRefFromGroupAssignedEnrolment("12345")
//
//    intercept[Exception](
//      await(response)
//    ).getMessage mustBe "Unexpected exception while getting group enrolments from ESP. Exception: Response code: 400, Response body: Multiple Errors with enrolments"
//  }
//}
