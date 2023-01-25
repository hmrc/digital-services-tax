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

package uk.gov.hmrc.digitalservicestax.connectors

import play.api.libs.json.{Format, JsObject, JsValue, Json}
import play.api.{Logger, Mode}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.data.DSTRegNumber
import uk.gov.hmrc.http.HttpReads.is2xx
import uk.gov.hmrc.http.{HttpClient, _}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxEnrolmentConnector @Inject() (
  val http: HttpClient,
  val mode: Mode,
  val appConfig: AppConfig
) extends DesHelpers {

  val logger: Logger = Logger(this.getClass)

  def subscribe(safeId: String, formBundleNumber: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse] = {
    import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
      http.PUT[JsValue, HttpResponse](subscribeUrl(formBundleNumber), requestBody(safeId, formBundleNumber)) map {
        case responseMessage if is2xx(responseMessage.status) =>
          responseMessage
        case responseMessage =>
          logger.error(s"Tax enrolment returned ${responseMessage.status}: ${responseMessage.body} for ${subscribeUrl(formBundleNumber)}")
          responseMessage
      }
  }

  def getSubscription(
    subscriptionId: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TaxEnrolmentsSubscription] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
      http.GET[TaxEnrolmentsSubscription](
        s"${appConfig.taxEnrolmentsUrl}/tax-enrolments/subscriptions/$subscriptionId"
      )
  }

  private def subscribeUrl(subscriptionId: String) =
    s"${appConfig.taxEnrolmentsUrl}/tax-enrolments/subscriptions/$subscriptionId/subscriber"

  private def requestBody(safeId: String, formBundleNumber: String): JsObject =
    Json.obj(
      "serviceName" -> appConfig.taxEnrolmentsServiceName,
      "callback"    -> s"${appConfig.taxEnrolmentsCallbackUrl}$formBundleNumber",
      "etmpId"      -> safeId
    )

}

case class TaxEnrolmentsSubscription(
  identifiers: Option[Seq[Identifier]],
  etmpId: String,
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
