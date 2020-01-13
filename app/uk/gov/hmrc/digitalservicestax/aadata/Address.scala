/*
 * Copyright 2019 HM Revenue & Customs
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

sealed trait Address {
  def line1: String
  def line2: String
  def line3: String
  def line4: String
  def line5: String  
  def countryCode: String
  def postalCode: String
  def lines: List[String] =
    line1 :: line2 :: line3 :: line4 :: line5 :: postalCode :: countryCode :: Nil
}

case class UkAddress(
  line1: String,
  line2: String, // "^[A-Za-z0-9 \\-,.&']{1,35}$"
  line3: String, // "^[A-Za-z0-9 \\-,.&']{1,35}$"
  line4: String, // "^[A-Za-z0-9 \\-,.&']{1,35}$"
  line5: String, // "^[A-Za-z0-9 \\-,.&']{1,35}$"  
  postalCode: String // "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}|BFPO\\s?[0-9]{1,10}$"
) extends Address {
  def countryCode: String = "GB"
}

case class ForeignAddress(
  line1: String,
  line2: String, // "^[A-Za-z0-9 \\-,.&']{1,35}$"
  line3: String, // "^[A-Za-z0-9 \\-,.&']{1,35}$"
  line4: String, // "^[A-Za-z0-9 \\-,.&']{1,35}$"
  line5: String, // "^[A-Za-z0-9 \\-,.&']{1,35}$"  
  postalCode: String, // "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}|BFPO\\s?[0-9]{1,10}$"
  countryCode: String
) extends Address
