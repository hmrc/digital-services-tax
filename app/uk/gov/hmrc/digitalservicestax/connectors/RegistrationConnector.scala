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

package uk.gov.hmrc.digitalservicestax
package connectors

import javax.inject.{Inject, Singleton}
import play.api.{Logger, Mode}
import play.api.libs.json.{JsValue, Json, Writes}
import uk.gov.hmrc.digitalservicestax.backend_data.{RegistrationResponse, RosmWithoutIDResponse}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.data.{Registration, SafeId}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

@Singleton
class RegistrationConnector @Inject()(
  val http: HttpClient,
  val mode: Mode,
  servicesConfig: ServicesConfig,
  appConfig: AppConfig)
  extends DesHelpers(servicesConfig) {

  val desURL: String = servicesConfig.baseUrl("des")
  val registerPath = "cross-regime/subscription/DST"

  def send(
    idType: String,
    idNumber: Option[String],
    request: Registration
  )(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[RegistrationResponse]] = {
    implicit val writes: Writes[Registration] = services.EeittInterface.registrationWriter
    (idType, idNumber) match {
      case (t, Some(i)) => {
        val result = desPost[JsValue, Option[RegistrationResponse]](s"$desURL/$registerPath/$t/$i", Json.toJson(request))(implicitly, implicitly, addHeaders, implicitly)
        if (appConfig.logRegResponse) Logger.debug(
          s"Registration response is ${Await.result(result, 20.seconds)}"
        )
        result
      }
      case _ =>
        Future.failed(new IllegalArgumentException(s"Missing idNumber for idType: $idType"))
    }
  }
}
