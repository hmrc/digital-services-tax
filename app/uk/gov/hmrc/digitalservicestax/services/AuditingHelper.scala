/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.libs.json._
import uk.gov.hmrc.digitalservicestax.controllers.CallbackNotification
import uk.gov.hmrc.digitalservicestax.data.BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.data._
import uk.gov.hmrc.digitalservicestax.services
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

object AuditingHelper {

  private def baseEvent: String => ExtendedDataEvent = ExtendedDataEvent(
    auditSource = "digital-services-tax",
    _: String
  )

  def buildCallbackAudit(
    body: CallbackNotification,
    formBundleNumber: FormBundleNumber,
    outcome: String,
    dstRegNo: Option[DSTRegNumber] = None
  ): ExtendedDataEvent = {
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
    outcome: String): ExtendedDataEvent = {

    val details = Json.obj(
      "subscriptionId" -> formBundleNumber,
      "outcome" -> outcome,
      "authProviderType" -> "GovernmentGateway",
      "authProviderId" -> providerId
    ).++(registrationJson(data))

    baseEvent("digitalServicesTaxRegistrationSubmitted").copy(detail =details)
  }

  private def registrationJson(data: Registration): JsObject =
    (JsPath \ "companyReg" \ "company" \ "address" \ "_type")
      .prune(Json.toJson(data)(registrationFormat)
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
