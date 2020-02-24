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

package uk.gov.hmrc.digitalservicestax.data

import enumeratum._

sealed trait ErrorResponseCode extends EnumEntry

object ErrorResponseCode extends Enum[ErrorResponseCode] {
  def values = findValues

  case object InvalidRegime extends ErrorResponseCode
  case object InvalidIdtype extends ErrorResponseCode
  case object InvalidIdnumber extends ErrorResponseCode
  case object InvalidPayload extends ErrorResponseCode

  /** The back end has indicated that business partner key information cannot be found for the id number. */
  case object NotFoundBpkey extends ErrorResponseCode

  /** The back end has indicated that the taxpayer profile cannot be found for the ID. */
  case object NotFoundId extends ErrorResponseCode
  case object DuplicateSubmission extends ErrorResponseCode
  case object ServerError extends ErrorResponseCode
  case object ServiceUnavailable extends ErrorResponseCode
}

case class ErrorResponse(
  code: ErrorResponseCode,
  reason: String
)
