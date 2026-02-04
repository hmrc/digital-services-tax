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

package uk.gov.hmrc.digitalservicestax.connectors

import play.api.libs.json.{Format, JsObject, Json}
import play.api.{Logger, Mode}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.data.enrolments.KeyValuePair
import uk.gov.hmrc.digitalservicestax.data.enrolments.Enrolments
import uk.gov.hmrc.digitalservicestax.data.{Address, DSTRegNumber, ForeignAddress, UkAddress}
import uk.gov.hmrc.digitalservicestax.test.TestConnector
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxEnrolmentConnector @Inject() (
  val http: HttpClientV2,
  val mode: Mode,
  val appConfig: AppConfig,
  testConnector: TestConnector
) extends DesHelpers {

  val logger: Logger = Logger(this.getClass)

  def subscribe(safeId: String, formBundleNumber: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse] = {
    import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
    if (appConfig.taxEnrolmentsEnabled) {
      http
        .put(url"${subscribeUrl(formBundleNumber)}")
        .withBody(requestBody(safeId, formBundleNumber))
        .execute[HttpResponse] map {
        case responseMessage if responseMessage.status >= 200 && responseMessage.status < 300 =>
          responseMessage
        case responseMessage                                                                  =>
          logger.error(
            s"Tax enrolment returned ${responseMessage.status}: ${responseMessage.body} for ${subscribeUrl(formBundleNumber)}"
          )
          responseMessage
      } recover {
        case e: UnauthorizedException => handleError(e, formBundleNumber)
        case e: BadRequestException   => handleError(e, formBundleNumber)
      }
    } else Future.successful[HttpResponse](HttpResponse(418, ""))
  }

  def getSubscription(
    subscriptionId: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TaxEnrolmentsSubscription] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    if (appConfig.taxEnrolmentsEnabled)
      http
        .get(
          url"${appConfig.taxEnrolmentsUrl}/tax-enrolments/subscriptions/$subscriptionId"
        )
        .execute[TaxEnrolmentsSubscription]
    else {
      testConnector.getSubscription(subscriptionId)
    }
  }

  def getPendingSubscriptionByGroupId(
    groupId: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TaxEnrolmentsSubscription]] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    http
      .get(
        url"${appConfig.taxEnrolmentsUrl}/tax-enrolments/groups/$groupId/subscriptions"
      )
      .execute[Seq[TaxEnrolmentsSubscription]]
      .map(_.find(_.state == "PENDING"))
  }

  private def handleError(e: HttpException, formBundleNumber: String): HttpResponse = {
    logger.error(s"Tax enrolment returned $e for ${subscribeUrl(formBundleNumber)}")
    HttpResponse(status = e.responseCode, json = Json.toJson(e.message), headers = Map.empty)
  }

  def subscribeUrl(subscriptionId: String) =
    s"${appConfig.taxEnrolmentsUrl}/tax-enrolments/subscriptions/$subscriptionId/subscriber"

  private def requestBody(safeId: String, formBundleNumber: String): JsObject =
    Json.obj(
      "serviceName" -> appConfig.taxEnrolmentsServiceName,
      "callback"    -> s"${appConfig.taxEnrolmentsCallbackUrl}$formBundleNumber",
      "etmpId"      -> safeId
    )

  val allocateDstEnrolmentToGroup = s"${appConfig.taxEnrolmentsUrl}/tax-enrolments/service/HMRC-DST-ORG/enrolment"

  def isAllocateDstGroupEnrolmentSuccess(address: Address, dstRegNumber: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Boolean] = {

    val verifierKey: KeyValuePair = address match {
      case ukAddress: UkAddress         => KeyValuePair("Postcode", ukAddress.postalCode)
      case nonUkAddress: ForeignAddress => KeyValuePair("NonUkCountryCode", nonUkAddress.countryCode)
    }

    val requestBody =
      Enrolments(List(verifierKey), List(KeyValuePair("DSTRefNumber", dstRegNumber)))

    http.put(url"$allocateDstEnrolmentToGroup").withBody(Json.toJson(requestBody)).execute[HttpResponse] map {
      case responseMessage if responseMessage.status == 204 => true
      case responseMessage                                  =>
        logger.error(
          s"Tax enrolment returned ${responseMessage.status}: ${responseMessage.body} for $allocateDstEnrolmentToGroup"
        )
        false
    } recover { case ex: Exception =>
      logger.error(
        s"Tax enrolment returned an exception when trying to call $allocateDstEnrolmentToGroup. Exception: ${ex.getMessage}"
      )
      false
    }
  }

}

case class TaxEnrolmentsSubscription(
  identifiers: Option[Seq[Identifier]],
  state: String,
  errorResponse: Option[String]
) {
  def getDSTNumber: Option[DSTRegNumber] =
    identifiers.getOrElse(Nil).collectFirst {
      case Identifier(_, value) if value.slice(2, 5) == "DST" => DSTRegNumber(value)
    }
}

object TaxEnrolmentsSubscription {
  implicit val format: Format[TaxEnrolmentsSubscription] = Json.format[TaxEnrolmentsSubscription]
}

case class Identifier(key: String, value: String)

object Identifier {
  implicit val format: Format[Identifier] = Json.format[Identifier]
}
