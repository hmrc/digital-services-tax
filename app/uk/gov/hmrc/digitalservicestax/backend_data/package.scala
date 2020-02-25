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

import play.api.libs.json.Json

package object backend {
    implicit val organisationResponseFormat = Json.format[OrganisationResponse]
    implicit val format = Json.format[IndividualResponse]
    implicit val rosmResponseAddressFormat = Json.format[RosmResponseAddress]
    implicit val rosmResponseContactDetailsFormat = Json.format[RosmResponseContactDetails]
    implicit val rosmRegisterResponseFormat = Json.format[RosmRegisterResponse]
}
