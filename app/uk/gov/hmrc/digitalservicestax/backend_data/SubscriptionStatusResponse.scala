/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.digitalservicestax.backend_data

import play.api.libs.json.{JsString, JsSuccess, Json, Reads}

case class SubscriptionStatusResponse(
  subscriptionStatus: SubscriptionStatus,
  idType: Option[String],
  idValue: Option[String]
)

object SubscriptionStatusResponse {
  implicit val subscriptionStatusReads: Reads[SubscriptionStatusResponse] = Json.reads[SubscriptionStatusResponse]
}

sealed trait SubscriptionStatus extends Product with Serializable

object SubscriptionStatus {

  case object Subscribed extends SubscriptionStatus

  case object Other extends SubscriptionStatus

  implicit val reads: Reads[SubscriptionStatus] =
    Reads {
      case JsString("SUCCESSFUL") => JsSuccess(Subscribed)
      case _                      => JsSuccess(Other)
    }
}
