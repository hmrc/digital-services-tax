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
import shapeless.tag._
import shapeless.{:: => _}

package object data extends SimpleJson {

  type UTR = String @@ UTR.Tag
  object UTR
      extends RegexValidatedString(
        "^[0-9]{10}$"
      )

  type SafeId = String @@ SafeId.Tag
  object SafeId
      extends RegexValidatedString(
        "^[A-Z0-9]{1,15}$"
      )

  type SapNumber = String @@ SapNumber.Tag
  object SapNumber
      extends RegexValidatedString(
        "^[a-zA-Z0-9]{10}$"
      )

  type FormBundleNumber = String @@ FormBundleNumber.Tag
  object FormBundleNumber
      extends RegexValidatedString(
        regex = "^[0-9]{12}$"
      )

  type InternalId = String @@ InternalId.Tag
  object InternalId
      extends RegexValidatedString(
        regex = "^Int-[a-f0-9-]*$"
      )

  type Postcode = String @@ Postcode.Tag
  object Postcode
      extends RegexValidatedString(
        """^[A-Z]{1,2}[0-9][0-9A-Z]?\s?[0-9][A-Z]{2}$""",
        _.trim.replaceAll("[ \\t]+", " ").toUpperCase
      )

  type Money = BigDecimal @@ Money.Tag
  object Money extends ValidatedType[BigDecimal] {
    def validateAndTransform(in: BigDecimal): Option[BigDecimal] =
      Some(in).filter(_.toString.matches("^[0-9]+(\\.[0-9]{1,2})?$"))

    implicit def mon: Monoid[Money] = new Monoid[Money] {
      val base: Monoid[BigDecimal]                    = implicitly[Monoid[BigDecimal]]
      override def combine(a: Money, b: Money): Money = Money(base.combine(a, b))
      override def empty: Money                       = Money(base.empty)
    }
  }

  type NonEmptyString = String @@ NonEmptyString.Tag
  object NonEmptyString extends ValidatedType[String] {
    def validateAndTransform(in: String): Option[String] =
      Some(in).filter(_.nonEmpty)
  }

  type CompanyName = String @@ CompanyName.Tag
  object CompanyName
      extends RegexValidatedString(
        regex = """^[a-zA-Z0-9 '&.-]{1,105}$"""
      )

  type AddressLine = String @@ AddressLine.Tag
  object AddressLine
      extends RegexValidatedString(
        regex = """^[a-zA-Z0-9 '&.-]{1,35}$"""
      )

  type RestrictiveString = String @@ RestrictiveString.Tag
  object RestrictiveString
      extends RegexValidatedString(
        """^[a-zA-Z'&-^]{1,35}$"""
      )

  type CountryCode = String @@ CountryCode.Tag
  object CountryCode
      extends RegexValidatedString(
        """^[A-Z][A-Z]$""",
        _.toUpperCase match {
          case "UK"  => "GB"
          case other => other
        }
      )

  type SortCode = String @@ SortCode.Tag
  object SortCode
      extends RegexValidatedString(
        """^[0-9]{6}$""",
        _.filter(_.isDigit)
      )

  type AccountNumber = String @@ AccountNumber.Tag
  object AccountNumber
      extends RegexValidatedString(
        """^[0-9]{8}$""",
        _.filter(_.isDigit)
      )

  type BuildingSocietyRollNumber = String @@ BuildingSocietyRollNumber.Tag
  object BuildingSocietyRollNumber
      extends RegexValidatedString(
        """^[A-Za-z0-9 -]{1,18}$"""
      )

  type AccountName = String @@ AccountName.Tag
  object AccountName
      extends RegexValidatedString(
        """^[a-zA-Z&^]{1,35}$"""
      )

  type IBAN = String @@ IBAN.Tag
  object IBAN extends ValidatedType[String] {
    override def validateAndTransform(in: String): Option[String] =
      Some(in).map(_.replaceAll("\\s+", "")).filter(Iban.isValid)
  }

  type PhoneNumber = String @@ PhoneNumber.Tag
  object PhoneNumber
      extends RegexValidatedString(
        // Regex which fits both eeitt_subscribe
        "^[A-Z0-9 \\-]{1,30}$"
      )

  type Email = String @@ Email.Tag
  object Email extends ValidatedType[String] {
    def validateAndTransform(email: String): Option[String] = {
      import org.apache.commons.validator.routines.EmailValidator
      Some(email).filter(EmailValidator.getInstance.isValid(_))
    }
  }

  type Percent = Float @@ Percent.Tag
  object Percent extends ValidatedType[Float] {
    def validateAndTransform(in: Float): Option[Float] =
      Some(in).filter { x =>
        (x >= 0 && x <= 100) && (BigDecimal(x.toString).scale <= 3)
      }

    implicit def mon: Monoid[Percent] = new Monoid[Percent] {
      val base: Monoid[Float]                               = implicitly[Monoid[Float]]
      override def combine(a: Percent, b: Percent): Percent = Percent(base.combine(a, b))
      override def empty: Percent                           = Percent(base.empty)
    }
  }

  type DSTRegNumber = String @@ DSTRegNumber.Tag
  object DSTRegNumber
      extends RegexValidatedString(
        "^([A-Z]{2}DST[0-9]{10})$"
      )

  implicit val orderDate = new cats.Order[LocalDate] {
    def compare(x: LocalDate, y: LocalDate): Int = x.compareTo(y)
  }
}
