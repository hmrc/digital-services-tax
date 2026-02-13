/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.data._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailConnector @Inject() (http: HttpClient, appConfig: AppConfig) {

  val logger = Logger(this.getClass)

  def sendConfirmationEmail(
    contact: ContactDetails,
    companyName: CompanyName,
    parentCompanyName: CompanyName,
    dstNumber: DSTRegNumber,
    paymentDeadline: Period
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    val params = Json.obj(
      "to"         -> Seq(contact.email.toString),
      "templateId" -> "dst_registration_accepted",
      "parameters" -> Json.obj(
        "dstNumber"            -> dstNumber.toString,
        "name"                 -> s"${contact.forename} ${contact.surname}",
        "companyName"          -> companyName.toString,
        "groupCompanyName"     -> parentCompanyName.toString,
        "paymentDeadline"      -> paymentDeadline.paymentDue.toString.replace("-", ""),
        "submitReturnDeadline" -> paymentDeadline.returnDue.toString.replace("-", "")
      ),
      "force"      -> false
    )

    sendEmail(params)
  }

  def sendSubmissionReceivedEmail(
    contact: ContactDetails,
    companyName: CompanyName,
    parentCompany: Option[Company]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    val params = Json.obj(
      "to"         -> Seq(contact.email.toString),
      "templateId" -> "dst_registration_received",
      "parameters" -> Json.obj(
        "name"             -> s"${contact.forename} ${contact.surname}",
        "companyName"      -> companyName.toString,
        "groupCompanyName" -> parentCompany.fold("unknown") {
          _.name.toString
        }
      ),
      "force"      -> false
    )

    sendEmail(params)
  }

  private def sendEmail(
    params: JsObject
  )(implicit
    hc: HeaderCarrier,
    ex: ExecutionContext
  ) =
    http.POST[JsValue, HttpResponse](s"${appConfig.emailUrl}/hmrc/email", params) map {
      case response if response.status == play.api.http.Status.ACCEPTED    =>
        logger.info("email send accepted")
        ()
      case response if response.status == play.api.http.Status.BAD_REQUEST =>
        logger.warn(s"email send rejected, ${response.status}.")
        ()
      case _                                                               =>
        logger.warn(s"Unexpected response from email service")
        ()
    }
}
