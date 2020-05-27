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

import backend_data.ReturnResponse
import config.DstConfig
import data._, BackendAndFrontendJson._
import services.JsonSchemaChecker

import java.time.LocalDate
import play.api.libs.json._
import play.api.{Logger, Mode}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

class ReturnConnector(
  val http: HttpClient,
  val mode: Mode,
  val config: DstConfig
)
  extends DesHelpers {

  val desURL: String = config.upstreamServices.des.baseUrl
  val registerPath = "cross-regime/subscription/DST"

  val desConfig = config.upstreamServices.des

  def getNextPendingPeriod(
    dstRegNo: DSTRegNumber    
  )(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Period] =
    getPeriods(dstRegNo).map{
      _.collectFirst { case (x, None) => x }
        .getOrElse(throw new NoSuchElementException)
    }

  def getPeriods(
    dstRegNo: DSTRegNumber    
  )(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[List[(Period, Option[LocalDate])]] = {
    val url = s"$desURL/enterprise/obligation-data/zdst/$dstRegNo/DST" +
      s"?from=${config.obligationStartDate}" +
      s"&to=${LocalDate.now.plusYears(1)}"

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

    JsonSchemaChecker(request,"return-submission")(writes)

    val url = s"$desURL/cross-regime/return/DST/zdst/$dstRegNo"
    val result = desPost[JsValue, ReturnResponse](
      url,
      Json.toJson(request)(writes)
    )

    if (config.logging.registerResponse) Logger.debug(
      s"Return response is ${Await.result(result, 20.seconds)}"
    )

    result
  }
}
