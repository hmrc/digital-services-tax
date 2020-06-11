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

package uk.gov.hmrc.digitalservicestax.actions

import java.net.URI

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, stubFor, urlPathEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.Status
import play.api.libs.json.{JsArray, JsObject, JsString, Json}
import play.api.mvc.{Result, Results}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{Enrolments, PlayAuthConnector}
import uk.gov.hmrc.digitalservicestax.data.BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.data.InternalId
import uk.gov.hmrc.digitalservicestax.util.FakeApplicationSpec
import uk.gov.hmrc.digitalservicestax.util.TestInstances._
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class AuthenticatedActionsTest extends FakeApplicationSpec
  with BeforeAndAfterEach
  with ScalaCheckDrivenPropertyChecks {

  lazy val authConnector: PlayAuthConnector = new DefaultAuthConnector(httpClient, servicesConfig)

  // Run wiremock server on local machine with specified port.
  val inet = new URI(authConnector.serviceUrl)
  val wireMockServer = new WireMockServer(wireMockConfig().port(inet.getPort))

  WireMock.configureFor(inet.getHost, inet.getPort)

  override def beforeEach {
    wireMockServer.start()
  }

  override def afterEach {
    wireMockServer.stop()
  }

  val action = new LoggedInAction(mcc, authConnector = authConnector)

  "it should execute a logger in action" in {
    val internal = arbitrary[InternalId].sample.value
    val enrolments = arbitrary[Enrolments].sample.value
    val credentialRole = arbitrary[Credentials].sample.value

    implicit val credentialWrites = Json.writes[Credentials]

    val jsonResponse = JsObject(Seq(
      Retrievals.allEnrolments.propertyNames.head -> JsArray(enrolments.enrolments.toSeq.map(Json.toJson(_))),
      Retrievals.internalId.propertyNames.head -> JsString(internal),
      Retrievals.credentials.propertyNames.head -> Json.toJson(credentialRole)
    ))

    stubFor(
      post(urlPathEqualTo(s"/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(Json.toJson(jsonResponse).toString())
        )
    )

    val loggedInReq = LoggedInRequest(
      internal,
      enrolments,
      credentialRole.providerId,
      FakeRequest()
    )

    val chain: Future[Result] = for {
      block <- action.invokeBlock(loggedInReq, { req: LoggedInRequest[_] =>
        Future.successful(Results.Ok(req.internalId))
      })
    } yield block

    Console.println(Helpers.contentAsString(chain)(1000 millis))

    whenReady(chain) { resp =>
      resp.header.status mustEqual Status.OK

    }
  }

}
