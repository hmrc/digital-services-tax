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

import cats.implicits.{none, _}
import play.api.libs.json._
import uk.gov.hmrc.digitalservicestax.controllers.CallbackNotification
import uk.gov.hmrc.digitalservicestax.data.{BackendAndFrontendJson, DSTRegNumber, FormBundleNumber, Registration, Return}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent


object AuditingHelper {

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
    ).++(Json.toJson(data)(BackendAndFrontendJson.registrationFormat).as[JsObject])

    baseEvent("digitalServicesTaxRegistrationSubmitted").copy(detail = details)
  }

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
    data: Return
  )(implicit hc: HeaderCarrier): ExtendedDataEvent = {

    val details = Json.obj(
      "dstRegistrationNumber" -> regNo.toString,
      "authProviderType" -> "GovernmentGateway",
      "authProviderId" -> providerId,
      "deviceId" -> hc.deviceID
    ).++(Json.toJson(data)(BackendAndFrontendJson.returnFormat).as[JsObject])

    baseEvent("returnSubmitted").copy(detail = details)
  }


}
