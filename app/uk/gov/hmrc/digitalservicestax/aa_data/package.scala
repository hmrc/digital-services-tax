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

import shapeless.{:: => _, _}
import tag._
import cats.implicits._
import cats.kernel.Monoid
import play.api.i18n.Messages
import java.time.LocalDate

import fr.marcwrobel.jbanking.iban.Iban

package object data extends SimpleJson {

  type UTR = String @@ UTR.Tag
  object UTR extends RegexValidatedString(
    "^[0-9]{10}$"
  )

  type SafeId = String @@ SafeId.Tag
  object SafeId extends RegexValidatedString(
    "^[A-Z0-9]{1,15}$"
  )

  type FormBundleNumber = String @@ FormBundleNumber.Tag
  object FormBundleNumber extends RegexValidatedString(
    regex = "^[0-9]{12}$"
  )

  type InternalId = String @@ InternalId.Tag
  object InternalId extends RegexValidatedString(
    regex = "^Int-[a-f0-9-]*$"
  )

  type Postcode = String @@ Postcode.Tag
  object Postcode extends RegexValidatedString(
    """^(GIR 0A{2})|((([A-Z][0-9]{1,2})|(([A-Z][A-HJ-Y][0-9]{1,2})|(([A-Z][0-9][A-Z])|([A-Z][A-HJ-Y][0-9]?[A-Z]))))[ ]?[0-9][A-Z]{2})$""",
    _.toUpperCase
  )

  type Money = BigDecimal

  type NonEmptyString = String @@ NonEmptyString.Tag
  object NonEmptyString extends ValidatedType[String]{
    def validateAndTransform(in: String): Option[String] =
      Some(in).filter(_.nonEmpty)
  }

  type CountryCode = String @@ CountryCode.Tag
  object CountryCode extends RegexValidatedString(
    """^[A-Z][A-Z]$""", 
    _.toUpperCase match {
      case "UK" => "GB"
      case other => other
    }
  )

  type SortCode = String @@ SortCode.Tag
  object SortCode extends RegexValidatedString(
    """^([0-9]{2}-){2}[0-9]{2}$""",
    {in =>
      val digitsOnly: String = in.filter(_.isDigit)
      digitsOnly.toList match {
        case aa :: ab :: ba :: bb :: ca :: cb :: Nil => s"$aa$ab-$ba$bb-$ca$cb"
        case _ => "bad"
      }
    }
  )

  type AccountNumber = String @@ AccountNumber.Tag
  object AccountNumber extends RegexValidatedString(
    """^[0-9]{8}$""",
    _.filter(_.isDigit)
  )

  type IBAN = String @@ IBAN.Tag
  object IBAN extends ValidatedType[String] {
    override def validateAndTransform(in: String): Option[String] = {
      Some(in).map(_.replaceAll("\\s+","")).filter(Iban.isValid)
    }
  }

  type PhoneNumber = String @@ PhoneNumber.Tag
  object PhoneNumber extends RegexValidatedString(
    "^[0-9 ]{6,30}$"
    // TODO: check phone number regex
//    """^(?:(?:\(?(?:0(?:0|11)\)?[\s-]?\(?|\+)44\)?[\s-]?(?:\(?0\)?[\s-]?)?)|(?:\(?0))(?:(?:\d{5}\)?[\s-]
//      |?\d{4,5})|(?:\d{4}\)?[\s-]?(?:\d{5}|\d{3}[\s-]?\d{3}))|(?:\d{3}\)?[\s-]?\d{3}[\s-]?\d{3,4})|
//      |(?:\d{2}\)?[\s-]?\d{4}[\s-]?\d{4}))(?:[\s-]?(?:x|ext\.?|\#)\d{3,4})?$""".stripMargin.replace("\n","") 
//    _.filter(_.isDigit)
  )

  type Email = String @@ Email.Tag
  object Email extends ValidatedType[String]{
    def validateAndTransform(email: String): Option[String] = {
      import org.apache.commons.validator.routines.EmailValidator
      Some(email).filter(EmailValidator.getInstance.isValid(_))
    }
  }

  type Percent = Byte @@ Percent.Tag
  object Percent extends ValidatedType[Byte] {
    def validateAndTransform(in: Byte): Option[Byte] = {
      Some(in).filter { x => x >= 0 && x <= 100 }
    }

    implicit def mon: Monoid[Percent] = new Monoid[Percent] {
      val base: Monoid[Byte] = implicitly[Monoid[Byte]]
      override def combine(a: Percent, b: Percent): Percent = Percent(base.combine(a, b))
      override def empty: Percent = Percent(base.empty)
    }
  }

  type DSTRegNumber = String @@ DSTRegNumber.Tag
  object DSTRegNumber extends RegexValidatedString(
    "^([A-Z]{2}DST[0-9]{10})$"
  )

  implicit val orderDate = new cats.Order[LocalDate] {
    def compare(x: LocalDate, y: LocalDate):Int = x.compareTo(y)
  }
}
