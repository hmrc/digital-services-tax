/*
 * Copyright 2021 HM Revenue & Customs
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

import cats.implicits._
import enumeratum.EnumFormats
import play.api.libs.json._
import shapeless.tag.@@
import uk.gov.hmrc.auth.core.Enrolment

import java.time.LocalDate
import java.time.format.DateTimeParseException
import scala.collection.immutable.ListMap
import play.api.libs.json.Json.fromJson

trait SimpleJson {

  def validatedStringFormat(A: ValidatedType[String], name: String) = new Format[String @@ A.Tag] {
    override def reads(json: JsValue): JsResult[String @@ A.Tag] = json match {
      case JsString(value) =>
        A.validateAndTransform(value) match {
          case Some(v) => JsSuccess(A(v))
          case None => JsError(s"Expected a valid $name, got $value instead")
        }
      case xs : JsValue => JsError(JsPath -> JsonValidationError(Seq(s"""Expected a valid $name, got $xs instead""")))
    }

    override def writes(o: String @@ A.Tag): JsValue = JsString(o)
  }

  implicit val nonEmptyStringFormat: Format[NonEmptyString] = new Format[NonEmptyString] {
    override def reads(json: JsValue): JsResult[NonEmptyString] = json match {
      case JsString(value) if value.nonEmpty => JsSuccess(NonEmptyString.apply(value))
      case _ => JsError((JsPath \ "value") -> JsonValidationError(Seq(s"Expected non empty string, got $json")))
    }

    override def writes(o: NonEmptyString): JsValue = JsString(o)
  }

  implicit val postcodeFormat                   = validatedStringFormat(Postcode, "postcode")
  implicit val phoneNumberFormat                = validatedStringFormat(PhoneNumber, "phone number")
  implicit val utrFormat                        = validatedStringFormat(UTR, "UTR")
  implicit val safeIfFormat                     = validatedStringFormat(SafeId, "SafeId")
  implicit val formBundleNoFormat               = validatedStringFormat(FormBundleNumber, "FormBundleNumber")
  implicit val internalIdFormat                 = validatedStringFormat(InternalId, "internal id")
  implicit val emailFormat                      = validatedStringFormat(Email, "email")
  implicit val countryCodeFormat                = validatedStringFormat(CountryCode, "country code")
  implicit val sortCodeFormat                   = validatedStringFormat(SortCode, "sort code")
  implicit val accountNumberFormat              = validatedStringFormat(AccountNumber, "account number")
  implicit val buildingSocietyRollNumberFormat  = validatedStringFormat(BuildingSocietyRollNumber, "building society roll number")
  implicit val accountNameFormat                = validatedStringFormat(AccountName, "account name")
  implicit val ibanFormat                       = validatedStringFormat(IBAN, "IBAN number")
  implicit val periodKeyFormat                  = validatedStringFormat(Period.Key, "Period Key")
  implicit val restrictiveFormat                = validatedStringFormat(RestrictiveString, "name")
  implicit val companyNameFormat                = validatedStringFormat(CompanyName, "company name")
  implicit val mandatoryAddressLineFormat       = validatedStringFormat(AddressLine, "address line")
  implicit val dstRegNoFormat                   = validatedStringFormat(DSTRegNumber, "Digital Services Tax Registration Number")

  implicit val moneyFormat: Format[Money] = new Format[Money] {
    override def reads(json: JsValue): JsResult[Money] = {
      json match {
        case JsNumber(value) =>
          Money.validateAndTransform(value.setScale(2)) match {
            case Some(validCode) => JsSuccess(Money(validCode))
            case None => JsError(s"Expected a valid monetary value, got $value instead.")
          }

        case xs: JsValue => JsError(
          JsPath -> JsonValidationError(Seq(s"""Expected a valid monetary value, got $xs instead"""))
        )
      }
    }

    override def writes(o: Money): JsValue = JsNumber(o)
  }

  implicit val percentFormat: Format[Percent] = new Format[Percent] {
    override def reads(json: JsValue): JsResult[Percent] = {
      json match {
        case JsNumber(value) =>
          Percent.validateAndTransform(value.toByte) match {
            case Some(validCode) => JsSuccess(Percent(validCode))
            case None => JsError(s"Expected a valid percentage, got $value instead.")
          }

        case xs: JsValue => JsError(
          JsPath -> JsonValidationError(Seq(s"""Expected a valid percentage, got $xs instead"""))
        )
      }
    }

    override def writes(o: Percent): JsValue = JsNumber(BigDecimal(o.toString))
  }

}

object BackendAndFrontendJson extends SimpleJson {

  implicit val foreignAddressFormat: OFormat[ForeignAddress] = Json.format[ForeignAddress]
  implicit val ukAddressFormat: OFormat[UkAddress] = Json.format[UkAddress]
  implicit val addressFormat: OFormat[Address] = Json.format[Address]
  implicit val companyFormat: OFormat[Company] = Json.format[Company]
  implicit val contactDetailsFormat: OFormat[ContactDetails] = Json.format[ContactDetails]
  implicit val companyRegWrapperFormat: OFormat[CompanyRegWrapper] = Json.format[CompanyRegWrapper]
  implicit val registrationFormat: OFormat[Registration] = Json.format[Registration]
  implicit val activityFormat: Format[Activity] = EnumFormats.formats(Activity)
  implicit val groupCompanyFormat: Format[GroupCompany] = Json.format[GroupCompany]

  import Enrolment.idFormat
  implicit val enrolmentWrites = Json.format[Enrolment]

  implicit val activityMapFormat: Format[Map[Activity, Percent]] = new Format[Map[Activity, Percent]] {
    override def reads(json: JsValue): JsResult[Map[Activity, Percent]] = {
      JsSuccess(json.as[Map[String, JsNumber]].map { case (k, v) =>
        Activity.values.find(_.entryName == k).get -> Percent.apply(v.value.toByte)
      })
    }

    override def writes(o: Map[Activity, Percent]): JsValue = {
      JsObject(o.toSeq.map { case (k, v) =>
        k.entryName -> JsNumber(BigDecimal(v.toString))
      })
    }
  }

  implicit def listMapReads[V](implicit formatV: Reads[V]): Reads[ListMap[String, V]] = new Reads[ListMap[String, V]] {
    def reads(json: JsValue) = json match {
      case JsObject(m) =>
        type Errors = Seq[(JsPath, Seq[JsonValidationError])]

        def locate(e: Errors, key: String): Seq[(JsPath, Seq[JsonValidationError])] = e.map {
          case (path, validationError) => (JsPath \ key) ++ path -> validationError
        }

        m.foldLeft(Right(ListMap.empty): Either[Errors, ListMap[String, V]]) {
          case (acc, (key, value)) => (acc, fromJson[V](value)(formatV)) match {
            case (Right(vs), JsSuccess(v, _)) => Right(vs + (key -> v))
            case (Right(_), JsError(e)) => Left(locate(e, key))
            case (Left(e), _: JsSuccess[_]) => Left(e)
            case (Left(e1), JsError(e2)) => Left(e1 ++ locate(e2, key))
          }
        }.fold(JsError.apply, res => JsSuccess(res))

      case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.jsobject"))))
    }
  }


  implicit val groupCompanyMapFormat: OFormat[ListMap[GroupCompany, Money]] = new OFormat[ListMap[GroupCompany, Money]] {
    override def reads(json: JsValue): JsResult[ListMap[GroupCompany, Money]] = {
      JsSuccess(json.as[ListMap[String, JsNumber]].map { case (k, v) =>
        k.split(":") match {
          case Array(name, utrS) =>
            GroupCompany(CompanyName(name), Some(UTR(utrS))) -> Money.apply(v.value.setScale(2))
          case Array(name) =>
            GroupCompany(CompanyName(name), None) -> Money.apply(v.value.setScale(2))
        }
      })
    }

    override def writes(o: ListMap[GroupCompany, Money]): JsObject = {
      JsObject(o.toSeq.map { case (k, v) =>
        s"${k.name}:${k.utr.getOrElse("")}" -> JsNumber(v)
      })
    }
  }

  implicit val domesticBankAccountFormat: OFormat[DomesticBankAccount] = Json.format[DomesticBankAccount]
  implicit val foreignBankAccountFormat: OFormat[ForeignBankAccount] = Json.format[ForeignBankAccount]
  implicit val bankAccountFormat: OFormat[BankAccount] = Json.format[BankAccount]
  implicit val repaymentDetailsFormat: OFormat[RepaymentDetails] = Json.format[RepaymentDetails]
  implicit val returnFormat: OFormat[Return] = Json.format[Return]

  implicit val periodFormat: OFormat[Period] = Json.format[Period]

  val readCompanyReg = new Reads[CompanyRegWrapper] {
    override def reads(json: JsValue): JsResult[CompanyRegWrapper] = {
      JsSuccess(CompanyRegWrapper(
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
        ).some
      ))
    }
  }

  implicit def basicDateFormatWrites: Format[LocalDate] = new Format[LocalDate] {

    def writes(dt: LocalDate): JsValue = JsString(dt.toString)

    def reads(i: JsValue): JsResult[LocalDate] = i match {
      case JsString(s) =>
        Either.catchOnly[DateTimeParseException]{
          LocalDate.parse(s)
        }.fold[JsResult[LocalDate]](e => JsError(e.getLocalizedMessage), JsSuccess(_))
      case o => JsError(s"expected a JsString(YYYY-MM-DD), got a $o")
    }
  }


  implicit def writePeriods: Writes[List[(Period, Option[LocalDate])]] = new Writes[List[(Period, Option[LocalDate])]] {
    override def writes(o: List[(Period, Option[LocalDate])]): JsValue = {

      val details = o.map { case (period, mapping) =>
        JsObject(
          Seq(
           "inboundCorrespondenceFromDate" -> Json.toJson(period.start),
           "inboundCorrespondenceToDate" -> Json.toJson(period.end),
           "inboundCorrespondenceDueDate" -> Json.toJson(period.returnDue),
           "periodKey" -> Json.toJson(period.key),
            "inboundCorrespondenceDateReceived" -> Json.toJson(mapping)
          )
        )

      }

      JsObject(Seq(
        "obligations" -> JsArray(details.map { dt =>
          JsObject(Seq(
            "obligationDetails" -> dt
          ))
        })
      ))
    }
  }

  implicit def readPeriods: Reads[List[(Period, Option[LocalDate])]] = new Reads[List[(Period, Option[LocalDate])]] {
    def reads(jsonOuter: JsValue): JsResult[List[(Period, Option[LocalDate])]] = {
      val JsArray(obligations) = { jsonOuter \ "obligations" }.as[JsArray]

      val periods = obligations.toList.flatMap { j =>
        val JsArray(elems) = {j \ "obligationDetails"}.as[JsArray]
        elems.toList
      }

      JsSuccess(periods.map { json =>
        (
          Period(
            {json \ "inboundCorrespondenceFromDate"}.as[LocalDate],
            {json \ "inboundCorrespondenceToDate"}.as[LocalDate],
            {json \ "inboundCorrespondenceDueDate"}.as[LocalDate],
            {json \ "periodKey"}.as[Period.Key]
          ),
          {json \ "inboundCorrespondenceDateReceived"}.asOpt[LocalDate]
        )
      })
    }
  }

  implicit def optFormat[A](implicit in: Format[A]) = new Format[Option[A]] {
    def reads(json: JsValue): JsResult[Option[A]] = json match {
      case JsNull => JsSuccess(None)
      case x => in.reads(x).map{Some(_)}
    }
    def writes(o: Option[A]): JsValue = o.fold(JsNull: JsValue)(in.writes)
  }

  implicit val unitFormat = new Format[Unit] {
    def reads(json: JsValue): JsResult[Unit] = json match {
      case JsNull => JsSuccess(())
      case JsObject(e) if e.isEmpty => JsSuccess(())
      case e => JsError(s"expected JsNull, encountered $e")
    }

    def writes(o: Unit): JsValue = JsNull
  }

}
