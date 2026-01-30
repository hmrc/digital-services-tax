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

import java.time.LocalDate
import java.time.format.DateTimeParseException

import cats.implicits._
import enumeratum.EnumFormats
import play.api.libs.json.Json.fromJson
import play.api.libs.json._
import uk.gov.hmrc.auth.core.Enrolment
import Enrolment.idFormat

import scala.collection.immutable.ListMap

trait SimpleJson {

  private def validatedStringFormat[T](A: ValidatedType[String], name: String, toT: String => T, fromT: T => String): Format[T] =
    new Format[T] {
      override def reads(json: JsValue): JsResult[T] = json match {
        case JsString(value) =>
          A.validateAndTransform(value) match {
            case Some(v) => JsSuccess(toT(v))
            case None    => JsError(s"Expected a valid $name, got $value instead")
          }
        case xs: JsValue     => JsError(JsPath -> JsonValidationError(Seq(s"""Expected a valid $name, got $xs instead""")))
      }

      override def writes(o: T): JsValue = JsString(fromT(o))
    }

  implicit val nonEmptyStringFormat: Format[NonEmptyString]     = validatedStringFormat(NonEmptyString, "NonEmptyString", NonEmptyString.tag, _.value)
  implicit val postcodeFormat: Format[Postcode]                 = validatedStringFormat(Postcode, "postcode", Postcode.tag, _.value)
  implicit val phoneNumberFormat: Format[PhoneNumber]           = validatedStringFormat(PhoneNumber, "phone number", PhoneNumber.tag, _.value)
  implicit val utrFormat: Format[UTR]                           = validatedStringFormat(UTR, "UTR", UTR.tag, _.value)
  implicit val safeIfFormat: Format[SafeId]                     = validatedStringFormat(SafeId, "SafeId", SafeId.tag, _.value)
  implicit val sapNumberFormat: Format[SapNumber]               = validatedStringFormat(SapNumber, "SapNumber", SapNumber.tag, _.value)
  implicit val formBundleNoFormat: Format[FormBundleNumber]     = validatedStringFormat(FormBundleNumber, "FormBundleNumber", FormBundleNumber.tag, _.value)
  implicit val internalIdFormat: Format[InternalId]             = validatedStringFormat(InternalId, "internal id", InternalId.tag, _.value)
  implicit val emailFormat: Format[Email]                       = validatedStringFormat(Email, "email", Email.tag, _.value)
  implicit val countryCodeFormat: Format[CountryCode]           = validatedStringFormat(CountryCode, "country code", CountryCode.tag, _.value)
  implicit val sortCodeFormat: Format[SortCode]                 = validatedStringFormat(SortCode, "sort code", SortCode.tag, _.value)
  implicit val accountNumberFormat: Format[AccountNumber]       = validatedStringFormat(AccountNumber, "account number", AccountNumber.tag, _.value)
  implicit val buildingSocietyRollNumberFormat: Format[BuildingSocietyRollNumber] =
    validatedStringFormat(BuildingSocietyRollNumber, "building society roll number", BuildingSocietyRollNumber.tag, _.value)
  implicit val accountNameFormat: Format[AccountName]           = validatedStringFormat(AccountName, "account name", AccountName.tag, _.value)
  implicit val ibanFormat: Format[IBAN]                         = validatedStringFormat(IBAN, "IBAN number", IBAN.tag, _.value)
  implicit val periodKeyFormat: Format[Period.Key]              = validatedStringFormat(Period.Key, "Period Key", Period.Key.tag, _.value)
  implicit val restrictiveFormat: Format[RestrictiveString]     = validatedStringFormat(RestrictiveString, "name", RestrictiveString.tag, _.value)
  implicit val companyNameFormat: Format[CompanyName]           = validatedStringFormat(CompanyName, "company name", CompanyName.tag, _.value)
  implicit val mandatoryAddressLineFormat: Format[AddressLine]  = validatedStringFormat(AddressLine, "address line", AddressLine.tag, _.value)
  implicit val dstRegNoFormat: Format[DSTRegNumber]             = validatedStringFormat(DSTRegNumber, "Digital Services Tax Registration Number", DSTRegNumber.tag, _.value)

  implicit val moneyFormat: Format[Money] = new Format[Money] {
    override def reads(json: JsValue): JsResult[Money] =
      json match {
        case JsNumber(value) =>
          Money.validateAndTransform(value.setScale(2)) match {
            case Some(validCode) => JsSuccess(Money.tag(validCode))
            case None            => JsError(s"Expected a valid monetary value, got $value instead.")
          }

        case xs: JsValue =>
          JsError(
            JsPath -> JsonValidationError(Seq(s"""Expected a valid monetary value, got $xs instead"""))
          )
      }

    override def writes(o: Money): JsValue = JsNumber(o.value)
  }

  implicit val percentFormat: Format[Percent] = new Format[Percent] {
    override def reads(json: JsValue): JsResult[Percent] =
      json match {
        case JsNumber(value) =>
          Percent.validateAndTransform(value) match {
            case Some(validCode) => JsSuccess(Percent.tag(validCode))
            case None            => JsError(s"Expected a valid percentage, got $value instead.")
          }

        case xs: JsValue =>
          JsError(
            JsPath -> JsonValidationError(Seq(s"""Expected a valid percentage, got $xs instead"""))
          )
      }

    override def writes(o: Percent): JsValue = JsNumber(BigDecimal(o.value.toString))
  }

}

object BackendAndFrontendJson extends SimpleJson {

  implicit val foreignAddressFormat: OFormat[ForeignAddress]       = Json.format[ForeignAddress]
//  implicit val ukAddressFormat: OFormat[UkAddress]                 = Json.format[UkAddress]
  implicit val addressFormat: OFormat[Address]                     = Json.format[Address]
  implicit val companyFormat: OFormat[Company]                     = Json.format[Company]
  implicit val contactDetailsFormat: OFormat[ContactDetails]       = Json.format[ContactDetails]
  implicit val companyRegWrapperFormat: OFormat[CompanyRegWrapper] = Json.format[CompanyRegWrapper]
  implicit val registrationFormat: OFormat[Registration]           = Json.format[Registration]
  implicit val activityFormat: Format[Activity]                    = EnumFormats.formats(Activity)
  implicit val groupCompanyFormat: Format[GroupCompany]            = Json.format[GroupCompany]
  implicit val enrolmentWrites: OFormat[Enrolment]                 = Json.format[Enrolment]
  implicit val ukAddressFormat: OFormat[UkAddress] = new OFormat[UkAddress] {
    override def reads(json: JsValue): JsResult[UkAddress] = for {
      line1 <- (json \ "line1").validate[AddressLine]
      line2 <- (json \ "line2").validateOpt[AddressLine]
      line3 <- (json \ "line3").validateOpt[AddressLine]
      line4 <- (json \ "line4").validateOpt[AddressLine]
      postcode <- (json \ "postalCode").validate[Postcode] // read `postalCode` from JSON
    } yield UkAddress(line1, line2, line3, line4, postcode) // pass into the `postCode` parameter

    override def writes(a: UkAddress): JsObject = Json.obj(
      "line1" -> Json.toJson(a.line1),
      "line2" -> Json.toJson(a.line2),
      "line3" -> Json.toJson(a.line3),
      "line4" -> Json.toJson(a.line4),
      "postalCode" -> Json.toJson(a.postCode) // write JSON key `postalCode`
    )
  }
  
  implicit val activityMapFormat: Format[Map[Activity, Percent]] = new Format[Map[Activity, Percent]] {
    override def reads(json: JsValue): JsResult[Map[Activity, Percent]] =
      JsSuccess(json.as[Map[String, JsNumber]].map { case (k, v) =>
        Activity.values.find(_.entryName == k).get -> Percent.apply(v.value)
      })

    override def writes(o: Map[Activity, Percent]): JsValue =
      JsObject(o.toSeq.map { case (k, v) =>
        k.entryName -> JsNumber(BigDecimal(v.value.toString))
      })
  }

  implicit def listMapReads[V](implicit formatV: Reads[V]): Reads[ListMap[String, V]] = new Reads[ListMap[String, V]] {
    def reads(json: JsValue) = json match {
      case JsObject(m) =>
        type Errors = scala.collection.Seq[(JsPath, scala.collection.Seq[JsonValidationError])]

        def locate(e: Errors, key: String): scala.collection.Seq[(JsPath, scala.collection.Seq[JsonValidationError])] =
          e.map { case (path, validationError) =>
            (JsPath \ key) ++ path -> validationError
          }

        m.foldLeft(Right(ListMap.empty): Either[Errors, ListMap[String, V]]) { case (acc, (key, value)) =>
          (acc, fromJson[V](value)(formatV)) match {
            case (Right(vs), JsSuccess(v, _)) => Right(vs + (key -> v))
            case (Right(_), JsError(e))       => Left(locate(e, key))
            case (Left(e), _: JsSuccess[_])   => Left(e)
            case (Left(e1), JsError(e2))      => Left(e1 ++ locate(e2, key))
          }
        }.fold(JsError.apply, res => JsSuccess(res))

      case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.jsobject"))))
    }
  }

  implicit val groupCompanyMapFormat: OFormat[ListMap[GroupCompany, Money]] =
    new OFormat[ListMap[GroupCompany, Money]] {
      override def reads(json: JsValue): JsResult[ListMap[GroupCompany, Money]] =
        JsSuccess(json.as[ListMap[String, JsNumber]].map { case (k, v) =>
          k.split(":") match {
            case Array(name, utrS) =>
              GroupCompany(CompanyName(name), Some(UTR(utrS))) -> Money.apply(v.value.setScale(2))
            case Array(name)       =>
              GroupCompany(CompanyName(name), None) -> Money.apply(v.value.setScale(2))
          }
        })

      override def writes(o: ListMap[GroupCompany, Money]): JsObject =
        JsObject(o.toSeq.map { case (k, v) =>
          s"${k.name}:${k.utr.getOrElse("")}" -> JsNumber(v.value)
        })
    }

  implicit val domesticBankAccountFormat: OFormat[DomesticBankAccount] = Json.format[DomesticBankAccount]
  implicit val foreignBankAccountFormat: OFormat[ForeignBankAccount]   = Json.format[ForeignBankAccount]
  implicit val bankAccountFormat: OFormat[BankAccount]                 = Json.format[BankAccount]
  implicit val repaymentDetailsFormat: OFormat[RepaymentDetails]       = Json.format[RepaymentDetails]
  implicit val returnFormat: OFormat[Return]                           = Json.format[Return]

  implicit val periodFormat: OFormat[Period] = Json.format[Period]

  val readCompanyReg = new Reads[CompanyRegWrapper] {
    override def reads(json: JsValue): JsResult[CompanyRegWrapper] =
      JsSuccess(
        CompanyRegWrapper(
          Company(
            {
              json \ "organisation" \ "organisationName"
            }.as[CompanyName], {
              json \ "address"
            }.as[Address]
          ),
          safeId = SafeId(
            {
              json \ "safeId"
            }.as[String]
          ).some,
          sapNumber = {
            json \ "sapNumber"
          }.asOpt[SapNumber]
        )
      )
  }

  implicit def basicDateFormatWrites: Format[LocalDate] = new Format[LocalDate] {

    def writes(dt: LocalDate): JsValue = JsString(dt.toString)

    def reads(i: JsValue): JsResult[LocalDate] = i match {
      case JsString(s) =>
        Either
          .catchOnly[DateTimeParseException] {
            LocalDate.parse(s)
          }
          .fold[JsResult[LocalDate]](e => JsError(e.getLocalizedMessage), JsSuccess(_))
      case o           => JsError(s"expected a JsString(YYYY-MM-DD), got a $o")
    }
  }

  implicit def writePeriods: Writes[List[(Period, Option[LocalDate])]] = new Writes[List[(Period, Option[LocalDate])]] {
    override def writes(o: List[(Period, Option[LocalDate])]): JsValue = {

      val details = o.map { case (period, mapping) =>
        JsObject(
          Seq(
            "inboundCorrespondenceFromDate"     -> Json.toJson(period.start),
            "inboundCorrespondenceToDate"       -> Json.toJson(period.end),
            "inboundCorrespondenceDueDate"      -> Json.toJson(period.returnDue),
            "periodKey"                         -> Json.toJson(period.key),
            "inboundCorrespondenceDateReceived" -> Json.toJson(mapping)
          )
        )

      }

      JsObject(
        Seq(
          "obligations" -> JsArray(details.map { dt =>
            JsObject(
              Seq(
                "obligationDetails" -> dt
              )
            )
          })
        )
      )
    }
  }

  implicit def readPeriods: Reads[List[(Period, Option[LocalDate])]] = new Reads[List[(Period, Option[LocalDate])]] {
    def reads(jsonOuter: JsValue): JsResult[List[(Period, Option[LocalDate])]] = {
      val JsArray(obligations) = { jsonOuter \ "obligations" }.as[JsArray]

      val periods = obligations.toList.flatMap { j =>
        val JsArray(elems) = { j \ "obligationDetails" }.as[JsArray]
        elems.toList
      }

      JsSuccess(periods.map { json =>
        (
          Period(
            { json \ "inboundCorrespondenceFromDate" }.as[LocalDate],
            { json \ "inboundCorrespondenceToDate" }.as[LocalDate],
            { json \ "inboundCorrespondenceDueDate" }.as[LocalDate],
            { json \ "periodKey" }.as[Period.Key]
          ),
          { json \ "inboundCorrespondenceDateReceived" }.asOpt[LocalDate]
        )
      })
    }
  }

  implicit def optFormat[A](implicit in: Format[A]): Format[Option[A]] = new Format[Option[A]] {
    def reads(json: JsValue): JsResult[Option[A]] = json match {
      case JsNull => JsSuccess(None)
      case x      => in.reads(x).map(Some(_))
    }
    def writes(o: Option[A]): JsValue             = o.fold(JsNull: JsValue)(in.writes)
  }

  implicit val unitFormat: Format[Unit] = new Format[Unit] {
    def reads(json: JsValue): JsResult[Unit] = json match {
      case JsNull                   => JsSuccess(())
      case JsObject(e) if e.isEmpty => JsSuccess(())
      case e                        => JsError(s"expected JsNull, encountered $e")
    }

    def writes(o: Unit): JsValue = JsNull
  }

}
