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
import play.api.libs.json.{JsValue, Json, Writes, Reads}
import uk.gov.hmrc.digitalservicestax.data.{DSTRegNumber, FinancialTransaction}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.data.{Registration, SafeId}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import java.time.LocalDate
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import java.net.URLEncoder.encode

@Singleton
class FinancialDataConnector @Inject()(
  val http: HttpClient,
  val mode: Mode,
  servicesConfig: ServicesConfig,
  appConfig: AppConfig,
  ec: ExecutionContext
) extends DesHelpers(servicesConfig) { 

  implicit val ec2 = ec

  val desURL: String = servicesConfig.baseUrl("des")

  /** Calls API#1166: Get Financial Data.
    *
    * Attempts to retrieve a list of financial line items.
    *
    * @param year If provided will show all items for that year, if omitted will only show 'open' items
    */
  def retrieveFinancialData(
    dstRegNo: DSTRegNumber,
    year: Option[Int] = Some(LocalDate.now.getYear)
  )(
    implicit hc: HeaderCarrier
  ): Future[List[FinancialTransaction]] = {

    val args: Map[String, Any] = Map(
      "onlyOpenItems"              -> year.isEmpty,
      "includeLocks"               -> false,
      "calculateAccruedInterest"   -> true,
      "customerPaymentInformation" -> true
    ) ++ (
      year match {
        case Some(y) =>
          Map(
            "dateFrom" -> s"$y-01-01",
            "dateTo"   -> s"$y-12-31"
          )
        case None => Map.empty[String, Any]
      }
    )

    def encodePair(in: (String, Any)): String =
      s"${encode(in._1, "UTF-8")}=${encode(in._2.toString, "UTF-8")}"

    val uri = s"$desURL/enterprise/financial-data/ZDST/$dstRegNo/DST?" ++
      args.map { encodePair }.mkString("&")

    implicit val readTransactionList: Reads[List[FinancialTransaction]] = ???

    http.GET[List[FinancialTransaction]](uri)
  }

}
