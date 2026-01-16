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

import play.api.Logger
import play.api.http.Status.{NOT_FOUND, NO_CONTENT, OK}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.data.GroupEnrolmentsResponse
import uk.gov.hmrc.http.{HttpClient, _}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EnrolmentStoreProxyConnector @Inject() (
  val http: HttpClient,
  val appConfig: AppConfig
) extends DesHelpers {

  val logger: Logger = Logger(this.getClass)

  val dstServiceName: String = "HMRC-DST-ORG"

  def es3DstUrl(groupId: String): String =
    appConfig.enrolmentStoreProxyUrl + s"/enrolment-store-proxy/enrolment-store/groups/$groupId/enrolments?service=$dstServiceName"

  def getDstRefFromGroupAssignedEnrolment(
    groupId: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] = {
    import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
    http.GET[HttpResponse](es3DstUrl(groupId)) map {
      case response if response.status == NO_CONTENT || response.status == NOT_FOUND => None
      case response if response.status == OK                                         =>
        response.json
          .validate[GroupEnrolmentsResponse]
          .getOrElse(throw new Exception("Unexpected Response body from enrolment store proxy"))
          .enrolments
          .find(enrolment => enrolment.isActivated && enrolment.service == dstServiceName)
          .flatMap(_.identifiers.find(_.key == "DSTRefNumber").map(_.value))
      case response                                                                  =>
        throw new Exception(s"Response code: ${response.status}, Response body: ${response.body}")
    } recover { case e: Exception =>
      throw new Exception(s"Unexpected exception while getting group enrolments from ESP. Exception: ${e.getMessage}")
    }
  }
}
