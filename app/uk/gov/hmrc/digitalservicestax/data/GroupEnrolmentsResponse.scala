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

package uk.gov.hmrc.digitalservicestax.data

import play.api.libs.json.{Json, OFormat}

case class GroupEnrolmentsResponse(enrolments: List[EnrolmentDetail])

object GroupEnrolmentsResponse {
  implicit val enrolmentReads: OFormat[EnrolmentDetail]           = Json.format[EnrolmentDetail]
  implicit val groupEnrolmentsReads: OFormat[GroupEnrolmentsResponse] = Json.format[GroupEnrolmentsResponse]
}

case class EnrolmentDetail(service: String, identifiers: Seq[ServiceIdentifier], state: String) {
  def isActivated: Boolean = state == "Activated"
}

object EnrolmentDetail {
  implicit val format: OFormat[EnrolmentDetail] = Json.format[EnrolmentDetail]
}

case class ServiceIdentifier(key: String, value: String)

object ServiceIdentifier {
  implicit val format: OFormat[ServiceIdentifier] = Json.format[ServiceIdentifier]
}
