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

package uk.gov.hmrc.digitalservicestax

import java.time.LocalDate

import cats.implicits._
import cats.kernel.Monoid
import fr.marcwrobel.jbanking.iban.Iban

package object data extends SimpleJson {

  object UTR
      extends RegexValidatedString(
        "^[0-9]{10}$"
      )
  type UTR = UTR.Type

  object SafeId
      extends RegexValidatedString(
        "^[A-Z0-9]{1,15}$"
      )
  type SafeId = SafeId.Type

  object SapNumber
      extends RegexValidatedString(
        "^[a-zA-Z0-9]{10}$"
      )
  type SapNumber = SapNumber.Type

  object FormBundleNumber
      extends RegexValidatedString(
        regex = "^[0-9]{12}$"
      )
  type FormBundleNumber = FormBundleNumber.Type

  object InternalId
      extends RegexValidatedString(
        regex = "^Int-[a-f0-9-]*$"
      )
  type InternalId = InternalId.Type

  object Postcode
      extends RegexValidatedString(
        """^[A-Z]{1,2}[0-9][0-9A-Z]?\s?[0-9][A-Z]{2}$""",
        _.trim.replaceAll("[ \\t]+", " ").toUpperCase
      )
  type Postcode = Postcode.Type

  object Money extends ValidatedType[BigDecimal] {
    def validateAndTransform(in: BigDecimal): Option[BigDecimal] =
      Some(in).filter(_.toString.matches("^[0-9]+(\\.[0-9]{1,2})?$"))

    given mon: Monoid[Money] with {
      override def combine(a: Money, b: Money): Money = Money((a: BigDecimal) + (b: BigDecimal))
      override def empty: Money                       = Money(BigDecimal(0))
    }
  }
  type Money = Money.Type

  object NonEmptyString extends ValidatedType[String] {
    def validateAndTransform(in: String): Option[String] =
      Some(in).filter(_.nonEmpty)
  }
  type NonEmptyString = NonEmptyString.Type

  object CompanyName
      extends RegexValidatedString(
        regex = """^[a-zA-Z0-9 '&.-]{1,105}$"""
      )
  type CompanyName = CompanyName.Type

  object AddressLine
      extends RegexValidatedString(
        regex = """^[a-zA-Z0-9 '&.-]{1,35}$"""
      )
  type AddressLine = AddressLine.Type

  object RestrictiveString
      extends RegexValidatedString(
        """^[a-zA-Z'&-^]{1,35}$"""
      )
  type RestrictiveString = RestrictiveString.Type

  object CountryCode
      extends RegexValidatedString(
        """^[A-Z][A-Z]$""",
        _.toUpperCase match {
          case "UK"  => "GB"
          case other => other
        }
      )
  type CountryCode = CountryCode.Type

  object SortCode
      extends RegexValidatedString(
        """^[0-9]{6}$""",
        _.filter(_.isDigit)
      )
  type SortCode = SortCode.Type

  object AccountNumber
      extends RegexValidatedString(
        """^[0-9]{8}$""",
        _.filter(_.isDigit)
      )
  type AccountNumber = AccountNumber.Type

  object BuildingSocietyRollNumber
      extends RegexValidatedString(
        """^[A-Za-z0-9 -]{1,18}$"""
      )
  type BuildingSocietyRollNumber = BuildingSocietyRollNumber.Type

  object AccountName
      extends RegexValidatedString(
        """^[a-zA-Z&^]{1,35}$"""
      )
  type AccountName = AccountName.Type

  object IBAN extends ValidatedType[String] {
    override def validateAndTransform(in: String): Option[String] =
      Some(in).map(_.replaceAll("\\s+", "")).filter(Iban.isValid)
  }
  type IBAN = IBAN.Type

  object PhoneNumber
      extends RegexValidatedString(
        // Regex which fits both eeitt_subscribe
        "^[A-Z0-9 \\-]{1,30}$"
      )
  type PhoneNumber = PhoneNumber.Type

  object Email extends ValidatedType[String] {
    def validateAndTransform(email: String): Option[String] = {
      import org.apache.commons.validator.routines.EmailValidator
      Some(email).filter(EmailValidator.getInstance.isValid(_))
    }
  }
  type Email = Email.Type

  object Percent extends ValidatedType[BigDecimal] {
    def validateAndTransform(in: BigDecimal): Option[BigDecimal] =
      Some(in).filter(x => (x >= 0 && x <= 100) && (x.scale <= 3))

    given mon: Monoid[Percent] with {
      override def combine(a: Percent, b: Percent): Percent = Percent((a: BigDecimal) + (b: BigDecimal))
      override def empty: Percent                           = Percent(BigDecimal(0))
    }
  }
  type Percent = Percent.Type

  object DSTRegNumber
      extends RegexValidatedString(
        "^([A-Z]{2}DST[0-9]{10})$"
      )
  type DSTRegNumber = DSTRegNumber.Type

  given Ordering[LocalDate] with {
    def compare(x: LocalDate, y: LocalDate): Int = x.compareTo(y)
  }
}
