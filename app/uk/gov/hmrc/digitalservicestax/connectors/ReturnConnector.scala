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
import java.time.{LocalDate, format}
import format.DateTimeParseException

import BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.services.JsonSchemaChecker

@Singleton
class ReturnConnector @Inject()(val http: HttpClient,
  val mode: Mode,
  val servicesConfig: ServicesConfig,
  appConfig: AppConfig)
  extends DesHelpers {

  val log = play.api.Logger(this.getClass())

  val desURL: String = servicesConfig.baseUrl("des")
  val registerPath = "cross-regime/subscription/DST"

  def getNextPendingPeriod(
    dstRegNo: DSTRegNumber    
  )(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Period] =
    getPeriods(dstRegNo).map{
      _.sortBy(_._1.start.toEpochDay).collectFirst { case (x, None) => x }
        .getOrElse(throw new NoSuchElementException)
    }

  def getPeriods(
    dstRegNo: DSTRegNumber    
  )(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[List[(Period, Option[LocalDate])]] = {
    val url = s"$desURL/enterprise/obligation-data/zdst/$dstRegNo/DST" +
      s"?from=${appConfig.obligationStartDate}" +
      s"&to=${LocalDate.now.plusYears(1)}"

    val ret = desGet[List[(Period, Option[LocalDate])]](url)
    ret.onComplete {
      case scala.util.Success(x) => x.foreach { case (p, r) =>
        log.debug(s"$p => $r")
      }
      case _ => ()
    }
    ret
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

    if (appConfig.logRegResponse) Logger.debug(
      s"Return response is ${Await.result(result, 20.seconds)}"
    )

    result
  }

  def doDebug()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    appConfig.debugRegNo match {
      case Some(dstRegNo) => 
        val originalCall: LocalDate = LocalDate.of(2020, 10, 9)
          val url = s"$desURL/enterprise/obligation-data/zdst/$dstRegNo/DST" +
          s"?from=${appConfig.obligationStartDate}" +
          s"&to=${originalCall.plusYears(1)}"
          val ret = desGet[JsValue](url)

        ret.map { json =>
          log.debug(s"JSON payload: $json")
        }
      case None =>
        Future.successful(())
    }
  }

}

