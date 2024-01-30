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

package uk.gov.hmrc.digitalservicestax
package connectors

import play.api.Mode
import play.api.libs.json._
import uk.gov.hmrc.digitalservicestax.backend_data.{RegistrationResponse, SubscriptionStatusResponse}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.controllers.AuditWrapper
import uk.gov.hmrc.digitalservicestax.data.{Registration, SapNumber}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegistrationConnector @Inject() (
  val http: HttpClient,
  val mode: Mode,
  val appConfig: AppConfig,
  val auditing: AuditConnector
) extends DesHelpers
    with AuditWrapper {

  private val registerPath              = "cross-regime/subscription/DST"
  private val getSubscriptionStatusPath = "cross-regime/subscription/DST"

  def send(
    idType: String,
    idNumber: String,
    request: Registration
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[RegistrationResponse] = {
    import services.EeittInterface.registrationWriter

    desPost[JsValue, Either[UpstreamErrorResponse, RegistrationResponse]](
      s"${appConfig.desURL}/$registerPath/$idType/$idNumber",
      Json.toJson(request)
    )(implicitly, implicitly, addHeaders, implicitly).map {
      case Right(value) => value
      case Left(e)      => throw UpstreamErrorResponse(e.message, e.statusCode)
    }
  }

  def getSubscriptionStatus(
    sapNumber: SapNumber
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[SubscriptionStatusResponse] =
    desGet[Either[UpstreamErrorResponse, SubscriptionStatusResponse]](
      s"${appConfig.desURL}/$getSubscriptionStatusPath/$sapNumber/status"
    )(implicitly, addHeaders, implicitly).map {
      case Right(value) => value
      case Left(e)      => throw UpstreamErrorResponse(e.message, e.statusCode)
    }
}
