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

import play.api.libs.json._
import play.api.{Logger, Mode}
import uk.gov.hmrc.digitalservicestax.backend_data.ReturnResponse
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.data.BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.data._
import uk.gov.hmrc.digitalservicestax.services.JsonSchemaChecker
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReturnConnector @Inject() (val http: HttpClient, val mode: Mode, val appConfig: AppConfig) extends DesHelpers {

  val logger: Logger = Logger(this.getClass)
  val registerPath   = "cross-regime/subscription/DST"

  def getNextPendingPeriod(
    dstRegNo: DSTRegNumber
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Period] =
    getPeriods(dstRegNo).map {
      _.sortBy(_._1.start.toEpochDay)
        .collectFirst { case (x, None) => x }
        .getOrElse(throw new NoSuchElementException)
    }

  def getPeriods(
    dstRegNo: DSTRegNumber
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[List[(Period, Option[LocalDate])]] = {
    val url = s"${appConfig.desURL}/enterprise/obligation-data/zdst/$dstRegNo/DST" +
      s"?from=${appConfig.obligationStartDate}" +
      s"&to=${LocalDate.now.plusYears(1)}"

    desGet[List[(Period, Option[LocalDate])]](url)
  }

  def send(
    dstRegNo: DSTRegNumber,
    period: Period,
    request: Return,
    isAmend: Boolean
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[ReturnResponse] = {

    implicit val writes: Writes[Return] = services.EeittInterface.returnRequestWriter(
      dstRegNo,
      period,
      isAmend
    )

    JsonSchemaChecker(request, "return-submission")(writes)
    logger.warn(s"${Json.toJson(request)(writes).toString()}")

    val url    = s"${appConfig.desURL}/cross-regime/return/DST/zdst/$dstRegNo"
    import uk.gov.hmrc.http.HttpReadsInstances._
    val result = desPost[JsValue, Either[UpstreamErrorResponse, ReturnResponse]](
      url,
      Json.toJson(request)(writes)
    ).map {
      case Right(value) => value
      case Left(e)      => throw UpstreamErrorResponse(e.message, e.statusCode)
    }

    result
  }
}
