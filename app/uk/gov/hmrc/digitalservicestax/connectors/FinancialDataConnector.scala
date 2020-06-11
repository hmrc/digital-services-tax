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

import config.AppConfig
import data.{DSTRegNumber, FinancialTransaction}

import java.net.URLEncoder.encode
import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import play.api.Mode
import play.api.libs.json._
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

@Singleton
class FinancialDataConnector @Inject()(
  val http: HttpClient,
  val mode: Mode,
  val servicesConfig: ServicesConfig,
  appConfig: AppConfig,
  implicit val ec: ExecutionContext
) extends DesHelpers { 

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

    implicit val readTransactionList = new Reads[List[FinancialTransaction]] {
      // dummy testing data until we have a viable sample payload to analyse from ETMP
      def reads(json: JsValue): JsResult[List[FinancialTransaction]] = {

        val JsArray(topLevelElements) = (json \ "financialTransactions").as[JsArray]
        val items = topLevelElements.flatMap { topLevel =>
          val JsArray(items) = (topLevel \ "items").as[JsArray]

          val subItems: List[FinancialTransaction] = items.flatMap { s =>
            (s \ "clearingDate").asOpt[LocalDate] match {
              case Some(clearingDate) =>
                val chargeType = (s \ "clearingReason").as[String]
                val amount = (s \ "amount").as[BigDecimal]
                List(FinancialTransaction(clearingDate, chargeType, amount))
              case None => List.empty[FinancialTransaction]
            }
          }.toList
          val chargeType = (topLevel \ "chargeType").as[String]
          val originalAmount = (topLevel \ "originalAmount").as[BigDecimal]          
          val transactionDate = (items.head \ "dueDate").as[LocalDate]
          FinancialTransaction(transactionDate, chargeType, -originalAmount) :: subItems
        }

        JsSuccess(items.toList.sortBy(_.date.toEpochDay()))
      }
    }

    http.GET[List[FinancialTransaction]](uri)
  }

}
