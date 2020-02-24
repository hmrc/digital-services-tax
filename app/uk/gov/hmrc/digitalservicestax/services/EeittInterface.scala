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
package services

import data._
import play.api.libs.json._
import cats.syntax.either._
import cats.{Id, ~>}
import cats.data.NonEmptySet
import java.time._, format.DateTimeFormatter
import enumeratum._, values._
import scala.collection.immutable.ListMap

sealed trait Honorific extends EnumEntry

object Honorific extends Enum[Honorific] {
  val values = findValues
  case object Mr extends Honorific
  case object Mrs extends Honorific
  case object Miss extends Honorific
  case object Ms extends Honorific
  case object Dr extends Honorific
  case object Sir extends Honorific
  case object Rev extends Honorific
  case object PersonalRepresentativeOf extends Honorific
  case object Professor extends Honorific
  case object Lord extends Honorific
  case object Lady extends Honorific
  case object Dame extends Honorific
}

case class ContactDetails(
  name: String, 
  telephone: String,
  emailAddress: String
)

sealed trait Identification

case object UnknownIdentification extends Identification

case class CustomerData(
  id: String,
  organisationName: String,
  title: Honorific,
  firstName: String,
  lastName: String,
  dateOfBirth: LocalDate
)

case class NominatedCompany(
  address: Address,
  telephoneNumber: String,
  emailAddress: String
)

case class UltimateOwner(
  name: String,
  reference: String,
  address: Address
)

case class SubscriptionRequest(
  identification: List[Identification],
  customer: CustomerData,
  nominatedCompany: NominatedCompany, 
  primaryPerson: ContactDetails,
  ultimateOwner: UltimateOwner,
  firstPeriod: (LocalDate, LocalDate)
)

sealed trait OrganisationType extends EnumEntry

object OrganisationType extends Enum[OrganisationType] {
  val values = findValues
  case object SoleProprietor              extends OrganisationType
  case object LimitedLiabilityPartnership extends OrganisationType
  case object Partnership                 extends OrganisationType
  case object UnincorporatedBody          extends OrganisationType
  case object Trust                       extends OrganisationType
  case object LimitedCompany              extends OrganisationType
  case object LloydsSyndicate             extends OrganisationType
}

object EeittInterface {

  def purgeNull(json: JsObject): JsObject = json match {
    case JsObject(inner) =>
      val data: Map[String, JsValue] = (inner collect {
        case (k, o: JsObject) => Some(k -> purgeNull(o))
        case (_, JsNull)      => None
        case (k, other)       => Some(k -> other)
      }).flatten.toMap
      JsObject(data)
  }

  private implicit def regimeSpecificWrites = new Writes[Map[String, String]] {
    def writes(value: Map[String, String]): JsValue =
      JsArray(
        value.toList.zipWithIndex flatMap {
          case ((key, ""), i) => Nil
          case ((key, value), i) =>
            List(Json.obj(
              "paramSequence" -> i.toString,
              "paramName" -> key,
              "paramValue" -> value
            ))
        }
      )
  }

  private implicit def enumEntryWrites[A <: EnumEntry] = new Writes[A] {
    def writes(a: A): JsValue = JsString(a.entryName)
  }

  implicit val subscriptionRequestWriter = new Writes[SubscriptionRequest] {

    implicit val trueFalseIndicatorType = new Writes[Boolean] {
      def writes(b: Boolean): JsValue = if (b) JsString("1") else JsString("0")
    }

    implicit val writesOrgType = new Writes[OrganisationType] {
      import OrganisationType._
      def writes(b: OrganisationType): JsValue = b match {
        case SoleProprietor              => JsString("1")
        case LimitedLiabilityPartnership => JsString("2")
        case Partnership                 => JsString("3")
        case UnincorporatedBody          => JsString("5")
        case Trust                       => JsString("6")
        case LimitedCompany              => JsString("7")
        case LloydsSyndicate             => JsString("12")
      }
    }

    implicit val writesHon = new Writes[Honorific] {
      import Honorific._

      def writes(b: Honorific): JsValue = b match {
        case Mr => JsString("0001")
        case Mrs => JsString("0002")
        case Miss => JsString("0003")
        case Ms => JsString("0004")
        case Dr => JsString("0005")
        case Sir => JsString("0006")
        case Rev => JsString("0007")
        case PersonalRepresentativeOf => JsString("0008")
        case Professor => JsString("0009")
        case Lord => JsString("0010")
        case Lady => JsString("0011")
        case Dame => JsString("0012")
      }
    }

    def strDate(d: LocalDate): String =
      d.format(format.DateTimeFormatter.BASIC_ISO_DATE)

    def writes(o: SubscriptionRequest): JsValue = {
      import o._

      val data = Json.obj(
        "registrationDetails" -> Json.obj(
          "isrScenario" -> "ZDS2",
          "commonDetails" -> Json.obj(
            "legalEntity" -> Json.obj(
	      "dateOfApplication" -> LocalDate.now.toString, // should this always be todays date?

	      "taxStartDate" -> firstPeriod._1 // should this always be todays date?
            ),
            "customerIdentificationNumber" -> Json.obj(
              //	      "custIDNumber" -> "???", // what should this be?
	      "noIdentifier" -> false,  // Customer Identifier Indicator where 1: True, 0: False. Expected to always be False for MDTP submissions
              "title" -> customer.title,
              "custFirstName" -> customer.firstName,
              "custLastName" -> customer.lastName,
              "custDOB" -> customer.dateOfBirth
//	      "organisationName" -> customer.organisationName,
            ),
            "businessContactDetails" -> Json.obj(
              "addressInputModeIndicator" -> "2",
              "addressLine1" -> nominatedCompany.address.line1,
              "addressLine2" -> Some(nominatedCompany.address.line2).filter(_.nonEmpty),
              "addressLine3" -> Some(nominatedCompany.address.line3).filter(_.nonEmpty),
              "addressLine4" -> Some(nominatedCompany.address.line4).filter(_.nonEmpty),
              "postCode" -> Some(nominatedCompany.address.postalCode).filter(_.nonEmpty),
              "addressNotInUK" -> nominatedCompany.address.isInstanceOf[ForeignAddress],
              "nonUKCountry" -> Some(nominatedCompany.address).collect{ case f: ForeignAddress => f.countryCode },
              "email" -> Some(nominatedCompany.emailAddress).filter(_.nonEmpty),
              "telephoneNumber" -> Some(nominatedCompany.telephoneNumber).filter(_.nonEmpty)
            )
          ),
          "regimeSpecificDetails" -> ListMap[String, String](
            "A_DST_PRIM_NAME" -> primaryPerson.name,
            "A_DST_PRIM_TELEPHONE" -> primaryPerson.telephone,
            "A_DST_PRIM_EMAIL" -> primaryPerson.emailAddress,
            "A_DST_GLOBAL_NAME" -> ultimateOwner.name,
            "A_DATA_ORIGIN" -> "1",
            "A_DST_PERIOD_END_DATE" -> strDate(firstPeriod._2),
            "A_TAX_START_DATE" -> strDate(firstPeriod._1),
            "A_BUS_ADR_LINE_5" -> nominatedCompany.address.line5,
            "A_CORR_ADR_LINE_1" -> ultimateOwner.address.line1,                                                
            "A_CORR_ADR_LINE_2" -> ultimateOwner.address.line2,                                    
            "A_CORR_ADR_LINE_3" -> ultimateOwner.address.line3,                        
            "A_CORR_ADR_LINE_4" -> ultimateOwner.address.line4,            
            "A_CORR_ADR_LINE_5" -> ultimateOwner.address.line5,
            "A_CORR_ADR_POST_CODE" -> ultimateOwner.address.postalCode,
            "A_CORR_ADR_COUNTRY_CODE" -> ultimateOwner.address.countryCode,
            "A_DST_GLOBAL_ID" -> ultimateOwner.reference
          )
        )
      )

      purgeNull(data)
    }
  }

  implicit val returnRequestWriter = new Writes[ReturnRequest] {
    def writes(o: ReturnRequest): JsValue = {
      import o._

      def bool(in: Boolean): String = if(in) "X" else " "

      import Activity._
      val activityEntries: Seq[(String, String)] =
        activity.toList flatMap { case (activityType,v) =>

          val key = activityType match {
            case SocialMedia => "SOCIAL"
            case SearchEngine => "SEARCH"
            case Marketplace => "MARKET"
          }

          List(
            s"DST_${key}_CHARGE_PROVISION" -> bool(v.alternateChargeProvision),
            s"DST_${key}_LOSS" -> bool(v.loss),
            s"DST_${key}_OP_MARGIN" -> v.margin.toString
          )
        }

      val subjectEntries: Seq[(String, String)] =
        Activity.values.map{ activityType =>
          val key = activityType match {
            case SocialMedia => "SOCIAL"
            case SearchEngine => "SEARCHENGINE"
            case Marketplace => "MARKETPLACE"
          }

          (s"DST_SUBJECT_${key}" -> bool(activity.isDefinedAt(activityType)))
        }

      val repaymentInfo: Seq[(String, String)] =
        repaymentDetails.fold(Seq.empty[(String, String)]){ bank => Seq(
          "BANK_NON_UK" -> bool(!bank.isUkAccount),
          "BANK_BSOC_NAME" -> bank.bankName, // Name of bank or building society CHAR40
          "BANK_SORT_CODE" -> bank.sortCode, // Branch sort code CHAR6
          "BANK_ACC_NO" -> bank.accountNumber, // Account number CHAR8
          "BANK_IBAN" -> bank.iban, // IBAN if non-UK bank account CHAR34
          "BANK_NAME" -> bank.bankName, // Name of account CHAR40
          "BUILDING_SOC_ROLE" -> bank.buildingSocietyRef // Building Society reference CHAR20
        )  }

      val breakdownEntries: Seq[(String, String)] = breakdown flatMap { e =>
        Seq(
          "DST_GROUP_MEMBER" -> e.memberName, // Group Member Company Name CHAR40
          "DST_GROUP_MEM_ID" -> e.utr, // Company registration reference number (UTR) CHAR40
          "DST_GROUP_MEM_LIABILITY" -> e.memberLiability.toString // DST liability amount per group member BETRW_KK
        )
      }

      val regimeSpecificDetails: Seq[(String, String)] = Seq(
        "REGISTRATION_NUMBER" -> dstRegNo, // MANDATORY ID Reference number ZGEN_FBP_REFERENCE
        "PERIOD_FROM" -> period.start.toString, // MANDATORY Period From  DATS
        "PERIOD_TO" -> period.start.toString, // MANDATORY Period To  DATS
        "DST_FIRST_RETURN" -> bool(isAmend), // Is this the first return you have submitted for this company and this accounting period? CHAR1
        "DST_RELIEF" -> finInfo.crossBorderRelief.toString, // Are you claiming relief for relevant cross-border transactions? CHAR1
        "DST_TAX_ALLOWANCE" -> finInfo.taxFreeAllowance.toString, // What tax-free allowance is being claimed against taxable revenues? BETRW_KK
        "DST_GROUP_LIABILITY" -> finInfo.totalLiability.toString, // MANDATORY Digital Services Group Total Liability BETRW_KK
        "DST_REPAYMENT_REQ" -> bool(repaymentDetails.isDefined), // Repayment for overpayment required? CHAR1
        "DATA_ORIGIN" -> "1" // MANDATORY Data origin CHAR2
      ) ++ subjectEntries ++ activityEntries ++ repaymentInfo ++ breakdownEntries

      val regimeSpecificJson = JsArray(
        regimeSpecificDetails.zipWithIndex map { case ((key, value), i) =>
          Json.obj(
            "paramSequence" -> i.toString,
            "paramName" -> key,
            "paramValue" -> value
          )
        }
      )

      Json.obj(
        "receivedAt" -> ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
        "periodFrom" -> period.start,
        "periodTo" -> period.end,
        "returnsDetails" -> Json.obj(
          "isrScenario" -> "ZDS1",
          "regimeSpecificDetails" -> regimeSpecificJson
        )
      )
    }
  }
}
