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

package uk.gov.hmrc.digitalservicestax.services

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import cats.implicits.{none, _}
import play.api.libs.json
import play.api.libs.json._
import uk.gov.hmrc.digitalservicestax.controllers.CallbackNotification
import uk.gov.hmrc.digitalservicestax.data._
import uk.gov.hmrc.digitalservicestax.services
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent


object AuditingHelper {

  implicit val foreignAddressFormat: OFormat[ForeignAddress] = Json.format[ForeignAddress]
  val defaulAddressFormat = Json.format[UkAddress]
  implicit val ukAddressFormat = new Format[UkAddress] {
    override def reads(json: JsValue): JsResult[UkAddress] =
      defaulAddressFormat.reads(json)

    override def writes(o: UkAddress): JsValue =
      defaulAddressFormat.writes(o).as[JsObject] ++ Json.obj("countryCode" -> "GB")
  }
  implicit val addressFormat: OFormat[Address] = Json.format[Address]
  implicit val companyFormat: OFormat[Company] = Json.format[Company]
  implicit val contactDetailsFormat: OFormat[ContactDetails] = Json.format[ContactDetails]
  implicit val companyRegWrapperFormat: OFormat[CompanyRegWrapper] = Json.format[CompanyRegWrapper]
  implicit val registrationFormat: OFormat[Registration] = Json.format[Registration]


  implicit def optFormatter[A](implicit innerFormatter: Format[A]): Format[Option[A]] =
    new Format[Option[A]] {
      def reads(json: JsValue): JsResult[Option[A]] = json match {
        case JsNull => JsSuccess(none[A])
        case a      => innerFormatter.reads(a).map{_.some}
      }
      def writes(o: Option[A]): JsValue =
        o.map{innerFormatter.writes}.getOrElse(JsNull)
    }


  private def baseEvent: String => ExtendedDataEvent = ExtendedDataEvent(
    auditSource = "digital-services-tax",
    _: String
  )

  def buildCallbackAudit(
    body: CallbackNotification,
    uri: String,
    formBundleNumber: FormBundleNumber,
    outcome: String,
    dstRegNo: Option[DSTRegNumber] = None
  )(implicit hc: HeaderCarrier): ExtendedDataEvent = {
    val details = Json.obj(
      "subscriptionId" -> formBundleNumber.toString,
      "dstRegistrationNumber" -> dstRegNo,
      "outcome" -> outcome,
      "errorMsg" -> body.errorResponse
    )
    baseEvent("digitalServicesTaxEnrolmentResponse").copy(detail = details)
  }

  def buildRegistrationAudit(
    data: Registration,
    providerId: String,
    formBundleNumber: Option[FormBundleNumber],
    outcome: String)(implicit hc: HeaderCarrier): ExtendedDataEvent = {

    val details = Json.obj(
      "subscriptionId" -> formBundleNumber,
      "outcome" -> outcome,
      "authProviderType" -> "GovernmentGateway",
      "authProviderId" -> providerId,
      "deviceId" -> hc.deviceID
    ).++(registrationJson(data))

    baseEvent("digitalServicesTaxRegistrationSubmitted").copy(detail =details)
  }

  private def registrationJson(data: Registration): JsObject =
    (JsPath \ "companyReg" \ "company" \ "address" \ "_type")
      .prune(Json.toJson(data)(this.registrationFormat)
        .as[JsObject]).get

  def buildReturnResponseAudit(
    outcome: String,
    errMsg: Option[String] = None
  ): ExtendedDataEvent = {
    baseEvent("returnSubmissionResponse").copy(detail = Json.obj(
      "responseStatus" -> outcome,
      "errorReason" -> errMsg
    ))
  }

  def buildReturnSubmissionAudit(
    regNo: DSTRegNumber,
    providerId: String,
    period: Period,
    data: Return,
    isAmend: Boolean
  )(implicit hc: HeaderCarrier): ExtendedDataEvent = {

    implicit val writes: Writes[Return] = services.EeittInterface.returnRequestWriter(
      regNo,
      period,
      isAmend,
      true,
      true
    )

    val details = Json.obj(
      "dstRegistrationNumber" -> regNo.toString,
      "authProviderType" -> "GovernmentGateway",
      "authProviderId" -> providerId,
      "deviceId" -> hc.deviceID
    ).++(Json.toJson(data)(writes).as[JsObject])

    baseEvent("returnSubmitted").copy(detail = details)
  }


}
