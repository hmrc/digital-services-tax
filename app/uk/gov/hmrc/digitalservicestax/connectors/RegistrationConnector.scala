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

package uk.gov.hmrc.digitalservicestax
package connectors

import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.{Logger, Mode}
import uk.gov.hmrc.digitalservicestax.backend_data.RegistrationResponse
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.controllers.AuditWrapper
import uk.gov.hmrc.digitalservicestax.data.Registration
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegistrationConnector @Inject()(
  val http: HttpClient,
  val mode: Mode,
  val servicesConfig: ServicesConfig,
  val auditing: AuditConnector,
  appConfig: AppConfig,
  ec: ExecutionContext
) extends DesHelpers with AuditWrapper {

  val logger: Logger = Logger(this.getClass)
  val desURL: String = servicesConfig.baseUrl("des")
  val registerPath = "cross-regime/subscription/DST"

  def send(
    idType: String,
    idNumber: String,
    request: Registration,
    providerId: String
  )(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[RegistrationResponse] = {

    import services.EeittInterface.registrationWriter

    import uk.gov.hmrc.http.HttpReadsInstances._
    val result = desPost[JsValue, Either[UpstreamErrorResponse,RegistrationResponse]](
      s"$desURL/$registerPath/$idType/$idNumber", Json.toJson(request)
    )(implicitly, implicitly, addHeaders, implicitly).map {
      case Right(value) => value
      case Left(e) => throw UpstreamErrorResponse(e.message, e.statusCode)
    }

    if (appConfig.logRegResponse) {
      result.onComplete{tr => logger.debug(s"Registration response is $tr")}
    }

    result
  }

}
