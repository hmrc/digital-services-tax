/*
 * Copyright 2022 HM Revenue & Customs
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

import cats.syntax.option._
import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.{Logger, Mode}
import uk.gov.hmrc.digitalservicestax.backend_data.RosmFormats.rosmWithoutIDResponseFormat
import uk.gov.hmrc.digitalservicestax.backend_data.RosmJsonReader.{InvalidAddressException, InvalidCompanyNameException, NotAnOrganisationException}
import uk.gov.hmrc.digitalservicestax.backend_data.{RosmRegisterWithoutIDRequest, RosmWithoutIDResponse}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.data.{percentFormat => _, _}
import uk.gov.hmrc.digitalservicestax.services.JsonSchemaChecker
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.HttpReads.Implicits.{readOptionOfNotFound, _}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RosmConnector @Inject()(
  val http: HttpClient,
  val mode: Mode,
  val servicesConfig: ServicesConfig,
  appConfig: AppConfig
) extends DesHelpers {

  val logger: Logger = Logger(this.getClass)
  val desURL: String = servicesConfig.baseUrl("des")

  val serviceURLWithId: String = "registration/organisation"
  val serviceURLWithoutId: String = "registration/02.00.00/organisation"

  def retrieveROSMDetailsWithoutID(
    request: RosmRegisterWithoutIDRequest
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[RosmWithoutIDResponse]] = {
    JsonSchemaChecker(request, "rosm-without-id-request")
    desPost[JsValue, Option[RosmWithoutIDResponse]](s"$desURL/$serviceURLWithoutId", Json.toJson(request))
  }

  def retrieveROSMDetails(utr: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[CompanyRegWrapper]] = {
    implicit val r = backend_data.RosmJsonReader
    val request: JsValue = Json.obj(
      "regime" -> "DST",
      "requiresNameMatch" -> false,
      "isAnAgent" -> false
    )

    desPost[JsValue, Option[CompanyRegWrapper]](
      s"$desURL/$serviceURLWithId/utr/$utr",
      request
    ).recover {
      case NotAnOrganisationException => None
      case InvalidCompanyNameException =>
        logger.warn("Invalid company name retrieved from ROSM")
        None
      case InvalidAddressException =>
        logger.warn("Invalid Address retrieved from ROSM")
        None
    }
  }

  def getSafeId(data: Registration)(implicit hc:HeaderCarrier, ec: ExecutionContext): Future[Option[SafeId]] = {
    retrieveROSMDetailsWithoutID(
      RosmRegisterWithoutIDRequest(
        isAnAgent = false,
        isAGroup = false,
        data.companyReg.company,
        data.contact
      )).map(_.fold(Option.empty[SafeId])(x => SafeId(x.safeId).some))
  }

}
