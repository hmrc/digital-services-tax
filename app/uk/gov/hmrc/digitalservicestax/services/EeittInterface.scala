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

package uk.gov.hmrc.digitalservicestax
package services

import data._
import play.api.libs.json._
import cats.syntax.either._
import cats.{Id, ~>}
import cats.data.NonEmptySet
import java.time._, format.DateTimeFormatter
import enumeratum._, values._

sealed abstract class Honorific(override val entryName: String) extends EnumEntry

object Honorific extends Enum[Honorific] {
  val values = findValues
  case object Mr extends Honorific("0001")
  case object Mrs extends Honorific("0002")
  case object Miss extends Honorific("0003")
  case object Ms extends Honorific("0004")
  case object Dr extends Honorific("0005")
  case object Sir extends Honorific("0006")
  case object Rev extends Honorific("0007")
  case object PersonalRepresentativeOf extends Honorific("0008")
  case object Professor extends Honorific("0009")
  case object Lord extends Honorific("0010")
  case object Lady extends Honorific("0011")
  case object Dame extends Honorific("0012")
}

case class Customer(
  title: Honorific,
  forename: String,
  surname: String,
  dateOfBirth: LocalDate
)

case class ContactDetails(
  address: Address,  
  telephone: String,
  mobile: Option[String], 
  fax: Option[String],
  emailAddress: String
)

case class SubscriptionRequest(
  orgName: String,
  orgType: OrganisationType,
  customer: Customer,
  contactDetails: ContactDetails,
  alternativeCorrespondence: Option[ContactDetails]
)

sealed abstract class OrganisationType(val value: Short) extends ShortEnumEntry

object OrganisationType extends ShortEnum[OrganisationType] {
  val values = findValues
  case object SoleProprietor              extends OrganisationType(1)
  case object LimitedLiabilityPartnership extends OrganisationType(2)
  case object Partnership                 extends OrganisationType(3)
  case object UnincorporatedBody          extends OrganisationType(5)
  case object Trust                       extends OrganisationType(6)
  case object LimitedCompany              extends OrganisationType(7)
  case object LloydsSyndicate             extends OrganisationType(12)
}

object EeittInterface {

  private implicit def regimeSpecificWrites = new Writes[Map[String, String]] {
    def writes(value: Map[String, String]): JsValue =
      JsArray(
        value.toList.zipWithIndex map { case ((key, value), i) =>
          Json.obj(
            "paramSequence" -> i.toString,
            "paramName" -> key,
            "paramValue" -> value
          )
        }
      )
  }

  private implicit def shortEnumEntryWrites[A <: ShortEnumEntry] = new Writes[A] {
    def writes(a: A): JsValue = JsNumber(a.value)
  }

  private implicit def enumEntryWrites[A <: EnumEntry] = new Writes[A] {
    def writes(a: A): JsValue = JsString(a.entryName)
  }

  implicit val subscriptionRequestWriter = new Writes[SubscriptionRequest] {

    implicit val trueFalseIndicatorType = new Writes[Boolean] {
      def writes(b: Boolean): JsValue = if (b) JsString("1") else JsString("0")
    }

    implicit def contactDetailsWrites = new Writes[ContactDetails] {
      def writes(value: ContactDetails): JsValue = {
        import value._
        Json.obj(
	  "addressNotInUK" -> {address match {
            case _: UkAddress => false
            case _ => true
          }},
	  "addressInputModeIndicator" -> "2",
	  "houseNumberName" -> address.line1,
	  "addressLine1" -> address.line2,
	  "addressLine2" -> address.line3,
	  "addressLine3" -> address.line4,
//	      "addressLine4" -> "BizContact Address Lfour",
	  "postCode" -> (Some(address) collect {case uk: UkAddress => uk.postalCode}),
	  "telephoneNumber" -> telephone,
	  "mobileNumber" -> mobile,
	  "email" -> emailAddress,
	  "fax" -> fax
        )
      }
    }

    def writes(o: SubscriptionRequest): JsValue = {
      import o._

      Json.obj(
        "registrationDetails" -> Json.obj(
          "isrScenario" -> "ZDS2",
          "commonDetails" -> Json.obj(
            "legalEntity" -> Json.obj(
	      "organisationType" -> orgType,
	      "dateOfApplication" -> LocalDate.now.toString, // should this always be todays date?
	      "taxStartDate" -> LocalDate.now.toString // should this always be todays date?
            ),
            "customerIdentificationNumber" -> Json.obj(
//	      "custIDNumber" -> "???", // what should this be?
	      "noIdentifier" -> false, // Customer Identifier Indicator where 1: True, 0: False. Expected to always be False for MDTP submissions
	      "organisationName" -> orgName,
	      "title" -> customer.title,
	      "custFirstName" -> customer.forename,
	      "custLastName" -> customer.surname,
	      "custDOB" -> customer.dateOfBirth,
	      "dataMismatchIndicator" -> false // ????
            ),
	    "aboutBusiness" -> Json.obj(
	      "organisationName" -> orgName,
	      "title" -> customer.title,
	      "firstName" ->  customer.forename,
	      "lastName" -> customer.surname,
	      "dateOfBirth" -> customer.dateOfBirth,
	      "tradingName" -> orgName
	    ),
            "businessContactDetails" -> contactDetails,
            "correspondenceAddressDifferent" -> alternativeCorrespondence.isDefined,
            "correspondenceContactDetails" -> alternativeCorrespondence
          ),
          "regimeSpecificDetails" -> Map[String, String](

          )
        ),
        "siteDetails" -> Json.obj(
          "isrScenario" -> "ZAG5", //???
          "formData" -> JsArray(
            List(
              Json.obj(
                "commonDetails" -> Json.obj(
                  "action" -> "1"
                )
              )
            )
          )
        )
      )
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
