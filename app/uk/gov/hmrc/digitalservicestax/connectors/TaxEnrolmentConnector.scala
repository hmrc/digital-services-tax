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

package uk.gov.hmrc.digitalservicestax.connectors

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Format, JsObject, JsValue, Json}
import play.api.{Logger, Mode}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.test.TestConnector
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{Await, ExecutionContext, Future}

@Singleton
class TaxEnrolmentConnector @Inject()(val http: HttpClient,
  val mode: Mode,
  servicesConfig: ServicesConfig,
  appConfig: AppConfig,
  testConnector: TestConnector
) extends DesHelpers(servicesConfig) {

  val callbackUrl: String = servicesConfig.getConfString("tax-enrolments.callback", "")
  val serviceName: String = servicesConfig.getConfString("tax-enrolments.serviceName", "")
  val enabled: Boolean = servicesConfig.getConfBool("tax-enrolments.enabled", true)
  lazy val taxEnrolmentsUrl: String = servicesConfig.baseUrl("tax-enrolments")

  def subscribe(safeId: String, formBundleNumber: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    if (enabled) {
      http.PUT[JsValue, HttpResponse](subscribeUrl(formBundleNumber), requestBody(safeId, formBundleNumber)) map {
        Result => {
          if (appConfig.logRegResponse) Logger.debug(
            s"Tax Enrolments response is $Result"
          )
          Result
        }
      } recover {
        case e: UnauthorizedException => handleError(e, formBundleNumber)
        case e: BadRequestException => handleError(e, formBundleNumber)
      }
    } else Future.successful[HttpResponse](HttpResponse.apply(418))
  }

  def getSubscription(subscriptionId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TaxEnrolmentsSubscription] = {
    if (enabled)
      http.GET[TaxEnrolmentsSubscription](s"$taxEnrolmentsUrl/tax-enrolments/subscriptions/$subscriptionId")
    else {
      testConnector.getSubscription(subscriptionId)
    }
  }

  private def handleError(e: HttpException, formBundleNumber: String): HttpResponse = {
    Logger.error(s"Tax enrolment returned $e for ${subscribeUrl(formBundleNumber)}")
    HttpResponse(e.responseCode, Some(Json.toJson(e.message)))
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
  etmpId: String, state: String,
  errorResponse: Option[String]
)

object TaxEnrolmentsSubscription {
  implicit val format: Format[TaxEnrolmentsSubscription] = Json.format[TaxEnrolmentsSubscription]
}

case class Identifier(key: String, value: String)

object Identifier {
  implicit val format: Format[Identifier] = Json.format[Identifier]
}