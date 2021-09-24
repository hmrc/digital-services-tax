/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Format, JsObject, JsValue, Json}
import play.api.{Logger, Mode}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.data.DSTRegNumber
import uk.gov.hmrc.digitalservicestax.test.TestConnector
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxEnrolmentConnector @Inject()(val http: HttpClient,
  val mode: Mode,
  val servicesConfig: ServicesConfig,
  appConfig: AppConfig,
  testConnector: TestConnector
) extends DesHelpers {

  val callbackUrl: String = servicesConfig.getConfString("tax-enrolments.callback", "")
  val serviceName: String = servicesConfig.getConfString("tax-enrolments.serviceName", "")
  val enabled: Boolean = servicesConfig.getConfBool("tax-enrolments.enabled", true)
  lazy val taxEnrolmentsUrl: String = servicesConfig.baseUrl("tax-enrolments")

  def subscribe(safeId: String, formBundleNumber: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    if (enabled) {
      http.PUT[JsValue, HttpResponse](subscribeUrl(formBundleNumber), requestBody(safeId, formBundleNumber)) map {
        Result => {
          Logger.debug(
            s"Tax Enrolments response is $Result"
          )
          Result
        }
      } recover {
        case e: UnauthorizedException => handleError(e, formBundleNumber)
        case e: BadRequestException => handleError(e, formBundleNumber)
      }
    } else Future.successful[HttpResponse](HttpResponse(418, ""))
  }

  def getSubscription(subscriptionId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TaxEnrolmentsSubscription] = {
    import uk.gov.hmrc.http.HttpReads.Implicits._
    if (enabled)
      http.GET[TaxEnrolmentsSubscription](
        s"$taxEnrolmentsUrl/tax-enrolments/subscriptions/$subscriptionId"
      )
//    (readRaw[TaxEnrolmentsSubscription], implicitly, implicitly)
    else {
      testConnector.getSubscription(subscriptionId)
    }
  }

  private def handleError(e: HttpException, formBundleNumber: String): HttpResponse = {
    Logger.error(s"Tax enrolment returned $e for ${subscribeUrl(formBundleNumber)}")
    HttpResponse(status = e.responseCode, json = Json.toJson(e.message), headers = Map.empty)
  }

  def subscribeUrl(subscriptionId: String) =
    s"$taxEnrolmentsUrl/tax-enrolments/subscriptions/$subscriptionId/subscriber"

  private def requestBody(safeId: String, formBundleNumber: String): JsObject = {
    Json.obj(
      "serviceName" -> serviceName,
      "callback" -> s"$callbackUrl$formBundleNumber",
      "etmpId" -> safeId
    )
  }

}

case class TaxEnrolmentsSubscription(
  identifiers: Option[Seq[Identifier]],
  etmpId: String,
  state: String,
  errorResponse: Option[String]
) {
  def getDSTNumber: Option[DSTRegNumber] = {

    identifiers.getOrElse(Nil).collectFirst {
      case Identifier(_, value) if value.slice(2, 5) == "DST" => DSTRegNumber(value)
    }
  }

}

object TaxEnrolmentsSubscription {
  implicit val format: Format[TaxEnrolmentsSubscription] = Json.format[TaxEnrolmentsSubscription]
}

case class Identifier(key: String, value: String)

object Identifier {
  implicit val format: Format[Identifier] = Json.format[Identifier]
}
