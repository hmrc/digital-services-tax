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

import shapeless.{:: => _, _}, tag._

package object data {

  type UTR = String @@ UTRTag

  object UTR extends RegexValidatedString[UTRTag](
    "^[0-9]{10}$"
  )
  type Postcode = String @@ PostcodeTag
  object Postcode extends RegexValidatedString[PostcodeTag](
    """^(GIR 0A{2})|((([A-Z][0-9]{1,2})|(([A-Z][A-HJ-Y][0-9]{1,2})|(([A-Z][0-9][A-Z])|([A-Z][A-HJ-Y][0-9]?[A-Z]))))\s?[0-9][A-Z]{2})$""", 
    _.toUpperCase
  )

  type Money = BigDecimal

}
