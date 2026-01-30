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
import cats.kernel.Monoid
import fr.marcwrobel.jbanking.iban.Iban

package object data extends SimpleJson {

  opaque type UTR = String
  object UTR
      extends RegexValidatedString(
        "^[0-9]{10}$"
      ) {
    type Validated = UTR
    def tag(value: String): UTR = value
  }

  opaque type SafeId = String
  object SafeId
      extends RegexValidatedString(
        "^[A-Z0-9]{1,15}$"
      ) {
    type Validated = SafeId
    def tag(value: String): SafeId = value
  }

  opaque type SapNumber = String
  object SapNumber
      extends RegexValidatedString(
        "^[a-zA-Z0-9]{10}$"
      ) {
    type Validated = SapNumber
    def tag(value: String): SapNumber = value
  }

  opaque type FormBundleNumber = String
  object FormBundleNumber
      extends RegexValidatedString(
        regex = "^[0-9]{12}$"
      ) {
    type Validated = FormBundleNumber
    def tag(value: String): FormBundleNumber = value
  }

  opaque type InternalId = String
  object InternalId
      extends RegexValidatedString(
        regex = "^Int-[a-f0-9-]*$"
      ) {
    type Validated = InternalId
    def tag(value: String): InternalId = value
  }

  opaque type Postcode = String
  object Postcode
      extends RegexValidatedString(
        """^[A-Z]{1,2}[0-9][0-9A-Z]?\s?[0-9][A-Z]{2}$""",
        _.trim.replaceAll("[ \\t]+", " ").toUpperCase
      ) {
    type Validated = Postcode
    def tag(value: String): Postcode = value
  }

  opaque type Money = BigDecimal
  object Money extends ValidatedType[BigDecimal] {
    type Validated = Money
    def validateAndTransform(in: BigDecimal): Option[BigDecimal] =
      Some(in).filter(_.toString.matches("^[0-9]+(\\.[0-9]{1,2})?$"))

    def tag(value: BigDecimal): Money = value

    given mon: Monoid[Money] with {
      override def combine(a: Money, b: Money): Money = tag((a: BigDecimal) + (b: BigDecimal))
      override def empty: Money                       = tag(BigDecimal(0))
    }
  }

  opaque type NonEmptyString = String
  object NonEmptyString extends ValidatedType[String] {
    type Validated = NonEmptyString
    def validateAndTransform(in: String): Option[String] =
      Some(in).filter(_.nonEmpty)
    def tag(value: String): NonEmptyString = value
  }

  opaque type CompanyName = String
  object CompanyName
      extends RegexValidatedString(
        regex = """^[a-zA-Z0-9 '&.-]{1,105}$"""
      ) {
    type Validated = CompanyName
    def tag(value: String): CompanyName = value
  }

  opaque type AddressLine = String
  object AddressLine
      extends RegexValidatedString(
        regex = """^[a-zA-Z0-9 '&.-]{1,35}$"""
      ) {
    type Validated = AddressLine
    def tag(value: String): AddressLine = value
  }

  opaque type RestrictiveString = String
  object RestrictiveString
      extends RegexValidatedString(
        """^[a-zA-Z'&-^]{1,35}$"""
      ) {
    type Validated = RestrictiveString
    def tag(value: String): RestrictiveString = value
  }

  opaque type CountryCode = String
  object CountryCode
      extends RegexValidatedString(
        """^[A-Z][A-Z]$""",
        _.toUpperCase match {
          case "UK"  => "GB"
          case other => other
        }
      ) {
    type Validated = CountryCode
    def tag(value: String): CountryCode = value
  }

  opaque type SortCode = String
  object SortCode
      extends RegexValidatedString(
        """^[0-9]{6}$""",
        _.filter(_.isDigit)
      ) {
    type Validated = SortCode
    def tag(value: String): SortCode = value
  }

  opaque type AccountNumber = String
  object AccountNumber
      extends RegexValidatedString(
        """^[0-9]{8}$""",
        _.filter(_.isDigit)
      ) {
    type Validated = AccountNumber
    def tag(value: String): AccountNumber = value
  }

  opaque type BuildingSocietyRollNumber = String
  object BuildingSocietyRollNumber
      extends RegexValidatedString(
        """^[A-Za-z0-9 -]{1,18}$"""
      ) {
    type Validated = BuildingSocietyRollNumber
    def tag(value: String): BuildingSocietyRollNumber = value
  }

  opaque type AccountName = String
  object AccountName
      extends RegexValidatedString(
        """^[a-zA-Z&^]{1,35}$"""
      ) {
    type Validated = AccountName
    def tag(value: String): AccountName = value
  }

  opaque type IBAN = String
  object IBAN extends ValidatedType[String] {
    type Validated = IBAN
    override def validateAndTransform(in: String): Option[String] =
      Some(in).map(_.replaceAll("\\s+", "")).filter(Iban.isValid)
    def tag(value: String): IBAN = value
  }

  opaque type PhoneNumber = String
  object PhoneNumber
      extends RegexValidatedString(
        // Regex which fits both eeitt_subscribe
        "^[A-Z0-9 \\-]{1,30}$"
      ) {
    type Validated = PhoneNumber
    def tag(value: String): PhoneNumber = value
  }

  opaque type Email = String
  object Email extends ValidatedType[String] {
    type Validated = Email
    def validateAndTransform(email: String): Option[String] = {
      import org.apache.commons.validator.routines.EmailValidator
      Some(email).filter(EmailValidator.getInstance.isValid(_))
    }
    def tag(value: String): Email = value
  }

  opaque type Percent = BigDecimal
  object Percent extends ValidatedType[BigDecimal] {
    type Validated = Percent
    def validateAndTransform(in: BigDecimal): Option[BigDecimal] =
      Some(in).filter { x => (x >= 0 && x <= 100) && (x.scale <= 3) }
      
    def tag(value: BigDecimal): Percent = value

    given mon: Monoid[Percent] with {
      override def combine(a: Percent, b: Percent): Percent = tag((a: BigDecimal) + (b: BigDecimal))
      override def empty: Percent = tag(BigDecimal(0))
    }
  }

  opaque type DSTRegNumber = String
  object DSTRegNumber
      extends RegexValidatedString(
        "^([A-Z]{2}DST[0-9]{10})$"
      ) {
    type Validated = DSTRegNumber
    def tag(value: String): DSTRegNumber = value
  }

  given Ordering[LocalDate] with {
    def compare(x: LocalDate, y: LocalDate): Int = x.compareTo(y)
  }

  // Extension methods for opaque Money type
  extension (m: Money) {
    def >(other: Int): Boolean = (m: BigDecimal) > other
    def >(other: Money): Boolean = (m: BigDecimal) > (other: BigDecimal)
    def <(other: Money): Boolean = (m: BigDecimal) < (other: BigDecimal)
    def -(other: Money): Money = Money.tag((m: BigDecimal) - (other: BigDecimal))
    def +(other: Money): Money = Money.tag((m: BigDecimal) + (other: BigDecimal))
  }

  // String concatenation support
  extension (s: RestrictiveString) {
    @scala.annotation.targetName("restrictiveStringConcat")
    def +(other: String): String = ((s: String) + other)
    
    @scala.annotation.targetName("restrictiveStringConcatRestricted")
    def +(other: RestrictiveString): String = ((s: String) + (other: String))
  }
}
