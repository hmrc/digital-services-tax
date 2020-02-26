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

import data.{percentFormat => _, _}
import data.BackendAndFrontendJson.rosmWithoutIDResponseFormat
import javax.inject.{Inject, Singleton}
import play.api.Mode
import play.api.libs.json._
import uk.gov.hmrc.digitalservicestax.backend_data.{RosmRegisterWithoutIDRequest, RosmWithoutIDResponse}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RosmConnector @Inject()(val http: HttpClient,
  val mode: Mode,
  servicesConfig: ServicesConfig)
  extends DesHelpers(servicesConfig) {

  val desURL: String = servicesConfig.baseUrl("des")

  val serviceURLWithId: String = "registration/organisation"
  val serviceURLWithoutId: String = "registration/02.00.00/organisation"

  def retrieveROSMDetailsWithoutID(request: RosmRegisterWithoutIDRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[RosmWithoutIDResponse]] = {
    implicit val writes: Writes[RosmRegisterWithoutIDRequest] = backend_data.RosmJsonWriter
    desPost[JsValue, Option[RosmWithoutIDResponse]](s"$desURL/$serviceURLWithoutId", Json.toJson(request))
  }

  def retrieveROSMDetails(utr: String)
    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Company]] = {
    val request: JsValue = Json.obj(
      "regime" -> servicesConfig.getString("etmp.sdil.regime")
    )
    implicit val readCo: Reads[Company] = backend.RosmJsonReader
    desPost[JsValue, Option[Company]](s"$desURL/$serviceURLWithId/utr/$utr", request)
  }
  
}
