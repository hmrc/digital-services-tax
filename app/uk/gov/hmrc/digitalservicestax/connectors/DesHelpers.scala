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
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.StringContextOps

import scala.concurrent.{ExecutionContext, Future}
/*
 * This trait is only used by the DES connectors, which add additional headers and use a DES bearer token.
 * Do not use this for any other connectors, as it will add unnecessary headers and use the wrong token.
 */
trait DesHelpers {

  def http: HttpClientV2
  def appConfig: AppConfig

  private def headers = Seq(
    HeaderNames.CONTENT_TYPE -> ContentTypes.JSON,
    "Environment"            -> appConfig.desEnvironment,
    "Authorization"          -> s"Bearer ${appConfig.desToken}"
  )

  def desGet[O](url: String)(implicit rds: HttpReads[O], hc: HeaderCarrier, ec: ExecutionContext): Future[O] =
    http
      .get(url"$url")
      .setHeader(headers: _*)
      .execute[O]

  def desPost[I, O](url: String, body: I)(implicit
    wts: Writes[I],
    rds: HttpReads[O],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[O] =
    http
      .post(url"$url")
      .setHeader(headers: _*)
      .withBody(Json.toJson(body))
      .execute[O]

}
