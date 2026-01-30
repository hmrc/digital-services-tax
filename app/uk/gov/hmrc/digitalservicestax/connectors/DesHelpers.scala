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

import play.api.http.{ContentTypes, HeaderNames}
import play.api.libs.json.{Json, Writes}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}

import scala.concurrent.{ExecutionContext, Future}

trait DesHelpers {

  def http: HttpClientV2
  def appConfig: AppConfig

  private def headers = Seq(
    HeaderNames.CONTENT_TYPE -> ContentTypes.JSON,
    "Environment"            -> appConfig.desEnvironment,
    "Authorization"          -> s"Bearer ${appConfig.desToken}"
  )

  def desGet[O](url: String)(implicit rds: HttpReads[O], hc: HeaderCarrier, ec: ExecutionContext): Future[O] =
    http.get(url"$url")(addHeaders).setHeader(headers: _*).execute[O]

  def desPost[I, O](url: String, body: I)(implicit
    wts: Writes[I],
    rds: HttpReads[O],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[O] =
    http.post(url"$url")(addHeaders).setHeader(headers: _*).withBody(Json.toJson(body)).execute[O]

  def addHeaders(implicit hc: HeaderCarrier): HeaderCarrier =
    hc.copy(authorization = None)

}
