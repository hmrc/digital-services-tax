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
import play.api.libs.json._
import uk.gov.hmrc.digitalservicestax.backend_data.ReturnResponse
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.data._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import java.time.LocalDate
@Singleton
class ReturnConnector @Inject()(val http: HttpClient,
  val mode: Mode,
  servicesConfig: ServicesConfig,
  appConfig: AppConfig)
  extends DesHelpers(servicesConfig) {

  val desURL: String = servicesConfig.baseUrl("des")
  val registerPath = "cross-regime/subscription/DST"

  def getPeriods(
    dstRegNo: DSTRegNumber    
  )(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[List[(Period, Option[LocalDate])]] = {

    implicit def readPeriods = new Reads[List[(Period, Option[LocalDate])]] {
      def reads(jsonOuter: JsValue): JsResult[List[(Period, Option[LocalDate])]] = {
        val JsArray(elems) = {jsonOuter \ "obligations" \ "obligationDetails"}.as[JsArray]
        JsSuccess(elems.map { json =>
          (
            Period(
              {json \ "inboundCorrespondenceFromDate"}.as[LocalDate],
              {json \ "inboundCorrespondenceToDate"}.as[LocalDate],
              {json \ "inboundCorrespondenceDueDate"}.as[LocalDate],              
              {json \ "periodKey"}.as[Period.Key]
            ),
            {json \ "inboundCorrespondenceDateReceived"}.asOpt[LocalDate]
          )
        }.toList)
        
      }
    }

    val url = s"$desURL/enterprise/obligation-data/zdst/$dstRegNo/DST" //"?from={from}&to={to}"
    desGet[List[(Period, Option[LocalDate])]](url)
  }

  def send(
    dstRegNo: DSTRegNumber,
    period: Period,
    request: Return,
    isAmend: Boolean
  )(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[ReturnResponse] = {

    implicit val writes: Writes[Return] = services.EeittInterface.returnRequestWriter(
      dstRegNo,
      period,
      isAmend
    )

    val url = s"$desURL/cross-regime/return/DST/eeits/$dstRegNo"
    val result = desPost[JsValue, ReturnResponse](
      url,
      Json.toJson(request)
    )

    if (appConfig.logRegResponse) Logger.debug(
      s"Return response is ${Await.result(result, 20.seconds)}"
    )

    result
  }
}