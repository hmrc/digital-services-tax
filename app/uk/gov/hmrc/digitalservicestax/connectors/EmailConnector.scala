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

package uk.gov.hmrc.digitalservicestax.connectors

import java.time.LocalDate

import javax.inject.{Inject, Singleton}
import play.api.Mode
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.digitalservicestax.data._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailConnector @Inject()(http: HttpClient, val mode: Mode, servicesConfig: ServicesConfig) {

  val emailUrl: String = servicesConfig.baseUrl("email")

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
        "dstNumber"  -> dstNumber.toString,
        "name" -> s"${contact.forename} ${contact.surname}",
        "companyName" -> companyName.toString,
        "groupCompanyName" -> parentCompanyName.toString,
        "paymentDeadline" -> paymentDeadline.paymentDue.toString.replace("-", ""),
        "submitReturnDeadline" -> paymentDeadline.returnDue.toString.replace("-", "")
      ),
      "force" -> false
    )

    http.POST[JsValue, HttpResponse](s"$emailUrl/hmrc/email", params) map { _ => () }
  }

  def sendSubmissionReceivedEmail(contact: ContactDetails, companyName: CompanyName, parentCompany: Option[Company])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Unit] = {
    val params = Json.obj(
      "to"         -> Seq(contact.email.toString),
      "templateId" -> "dst_registration_received",
      "parameters" -> Json.obj(
        "name" -> s"${contact.forename} ${contact.surname}",
        "companyName" -> companyName.toString,
        "groupCompanyName" -> parentCompany.fold("unknown") {
          _.name.toString
        }
      ),
      "force" -> false
    )

    http.POST[JsValue, HttpResponse](s"$emailUrl/hmrc/email", params) map { _ =>
      ()
    }
  }
}
