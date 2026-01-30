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
package services

import java.time.format.DateTimeFormatter
import java.time.{Period => _, _}

import play.api.libs.json._
import uk.gov.hmrc.digitalservicestax.data._

import scala.collection.immutable.ListMap

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

  implicit def regimeSpecificWrites: Writes[ListMap[String, String]] = new Writes[ListMap[String, String]] {
    def writes(value: ListMap[String, String]): JsValue =
      JsArray(
        value.toList.zipWithIndex flatMap {
          case ((_, ""), _)    => Nil
          case ((key, value), _) =>
            List(
              Json.obj(
                "paramSequence" -> "01",
                "paramName"     -> key,
                "paramValue"    -> value
              )
            )
        }
      )
  }

  def strDate(d: LocalDate): String =
    d.format(format.DateTimeFormatter.BASIC_ISO_DATE)

  implicit val registrationWriter: Writes[Registration] = new Writes[Registration] {
    implicit val trueFalseIndicatorType: Writes[Boolean] = new Writes[Boolean] {
      def writes(b: Boolean): JsValue = if (b) JsString("1") else JsString("0")
    }

    def writes(o: Registration): JsValue = {
      import o._

      val contactAddress = alternativeContact getOrElse companyReg.company.address

      val data = Json.obj(
        "registrationDetails" -> Json.obj(
          "isrScenario"           -> "ZDS2",
          "commonDetails"         -> Json.obj(
            "legalEntity"                  -> Json.obj(
              "dateOfApplication" -> LocalDate.now.toString,
              "taxStartDate"      -> dateLiable
            ),
            "customerIdentificationNumber" -> Json.obj(
              "noIdentifier"  -> o.companyReg.useSafeId, // Expected to always be False for MDTP submissions, except for Rosm without ID route
              "custFirstName" -> contact.forename.value,
              "custLastName"  -> contact.surname.value
            ),
            "businessContactDetails"       -> Json.obj(
              "addressInputModeIndicator" -> "2",
              "addressLine1"              -> contactAddress.line1.value,
              "addressLine2"              -> Some(contactAddress.line2).filter(_.nonEmpty),
              "addressLine3"              -> Some(contactAddress.line3).filter(_.nonEmpty),
              "addressLine4"              -> Some(contactAddress.line4).filter(_.nonEmpty),
              "postCode"                  -> Some(contactAddress.postalCode).filter(_.nonEmpty),
              "addressNotInUK"            -> contactAddress.isInstanceOf[ForeignAddress],
              "nonUKCountry"              -> Some(contactAddress).collect { case f: ForeignAddress => f.countryCode.value },
              "email"                     -> Some(contact.email.value).filter(_.nonEmpty),
              "telephoneNumber"           -> Some(contact.phoneNumber.value).filter(_.nonEmpty)
            )
          ),
          "regimeSpecificDetails" -> ListMap[String, String](
            "A_DST_PRIM_NAME"         -> { contact.forename.value + " " + contact.surname.value },
            "A_DST_PRIM_TELEPHONE"    -> contact.phoneNumber.value,
            "A_DST_PRIM_EMAIL"        -> contact.email.value,
            "A_DST_GLOBAL_NAME"       -> ultimateParent.fold("")(_.name.value),
            "A_DATA_ORIGIN"           -> "1",
            "A_DST_PERIOD_END_DATE"   -> strDate(accountingPeriodEnd),
            "A_TAX_START_DATE"        -> strDate(dateLiable),
            "A_CORR_ADR_LINE_1"       -> companyReg.company.address.line1.value,
            "A_CORR_ADR_LINE_2"       -> companyReg.company.address.line2.map(_.value).getOrElse(""),
            "A_CORR_ADR_LINE_3"       -> companyReg.company.address.line3.map(_.value).getOrElse(""),
            "A_CORR_ADR_LINE_4"       -> companyReg.company.address.line4.map(_.value).getOrElse(""),
            "A_CORR_ADR_POST_CODE"    -> companyReg.company.address.postalCode,
            "A_CORR_ADR_COUNTRY_CODE" -> companyReg.company.address.countryCode.value
          )
        )
      )

      purgeNull(data)
    }
  }

  def returnRequestWriter(
    dstRegNo: DSTRegNumber,
    period: Period,
    isAmend: Boolean = false,
    showReliefAmount: Boolean = false,
    forAudit: Boolean = false
  ) = new Writes[Return] {
    def writes(o: Return): JsValue = {
      import o._

      def bool(in: Boolean): String = if (in) "X" else " "

      import Activity._
      val activityEntries: Seq[(String, String)] =
        alternateCharge.toList flatMap { case (activityType, v) =>
          val key = activityType match {
            case SocialMedia       => "SOCIAL"
            case SearchEngine      => "SEARCH"
            case OnlineMarketplace => "MARKET"
          }

          List(
            s"A_DST_${key}_CHARGE_PROVISION" -> bool(true),
            s"A_DST_${key}_LOSS"             -> bool(v.value == 0),
            s"A_DST_${key}_OP_MARGIN"        -> v.toString
          )
        }

      val subjectEntries: Seq[(String, String)] =
        Activity.values.map { activityType =>
          val key = activityType match {
            case SocialMedia       => "SOCIAL"
            case SearchEngine      => "SEARCHENGINE"
            case OnlineMarketplace => "MARKETPLACE"
          }

          s"A_DST_SUBJECT_$key" -> bool(reportedActivities.contains(activityType))
        }

      val repaymentInfo: Seq[(String, String)] =
        repayment.fold(Seq.empty[(String, String)]) {
          case RepaymentDetails(acctName, DomesticBankAccount(sortCode, acctNo, bsNo)) =>
            Seq[(String, String)](
              "A_BANK_NAME"      -> acctName.value, // Name of account CHAR40
              "A_BANK_NON_UK"    -> bool(false),
              "A_BANK_SORT_CODE" -> sortCode.value, // Branch sort code CHAR6
              "A_BANK_ACC_NO"    -> acctNo.value // Account number CHAR8
            ) ++ bsNo
              .filter(_.value.nonEmpty)
              .map { a =>
                "A_BUILDING_SOC_ROLE" -> a.value // Building Society reference CHAR20
              }
              .toSeq

          case RepaymentDetails(acctName, ForeignBankAccount(iban)) =>
            Seq(
              "A_BANK_IBAN" -> iban.value, // IBAN if non-UK bank account CHAR34
              "A_BANK_NAME" -> acctName.value // Name of account CHAR40
            )
        }

      // N.B. not required for ETMP (yet) but needed for auditing
      val reliefAmount: Seq[(String, String)] =
        if (showReliefAmount) Seq("A_DST_RELIEF_AMOUNT" -> crossBorderReliefAmount.toString)
        else Seq.empty[(String, String)]

      val dstTaxAllowance: Seq[(String, String)] = allowanceAmount
        .map { x =>
          Seq(
            "A_DST_TAX_ALLOWANCE" -> x.toString
          ) // What tax-free allowance is being claimed against taxable revenues? BETRW_KK
        }
        .getOrElse(Seq.empty[(String, String)])

      val regimeSpecificDetails: Seq[(String, String)] = Seq(
        "A_REGISTRATION_NUMBER" -> dstRegNo.value, // MANDATORY ID Reference number ZGEN_FBP_REFERENCE
        "A_PERIOD_FROM"         -> strDate(period.start), // MANDATORY Period From  DATS
        "A_PERIOD_TO"           -> strDate(period.end), // MANDATORY Period To  DATS
        "A_DST_FIRST_RETURN"    -> bool(
          !isAmend
        ), // Is this the first return you have submitted for this company and this accounting period? CHAR1
        "A_DST_RELIEF"          -> bool(
          crossBorderReliefAmount > 0
        ), // Are you claiming relief for relevant cross-border transactions? CHAR1
        "A_DST_GROUP_LIABILITY" -> totalLiability.value.toString, // MANDATORY Digital Services Group Total Liability BETRW_KK
        "A_DST_REPAYMENT_REQ"   -> bool(repayment.isDefined), // Repayment for overpayment required? CHAR1
        "A_DATA_ORIGIN"         -> "1" // MANDATORY Data origin CHAR2
      ) ++ subjectEntries ++ activityEntries ++ repaymentInfo ++ reliefAmount ++ dstTaxAllowance

      def groupMemberFields(company: GroupCompany, amt: Money): Seq[(String, String)] =
        Seq(
          "A_DST_GROUP_MEMBER"        -> company.name.value, // Group Member Company Name CHAR40
          "A_DST_GROUP_MEM_LIABILITY" -> amt.value.toString, // DST liability amount per group member BETRW_KK
          "A_DST_GROUP_MEM_ID"        -> company.utr.map(_.value).getOrElse("NA") // UTR is optional for user but required in api
        )

      val breakdownEntries = companiesAmount.toList.map { case (company, amt) =>
        groupMemberFields(company, amt)
      }

      val regimeSpecificJson =
        if (forAudit) {
          JsArray(
            regimeSpecificDetails.map { case (key, value) =>
              Json.obj(
                key -> value
              )
            } ++ breakdownEntries.map { xs =>
              JsObject(
                xs.map { case (k, v) => k -> JsString(v) }
              )
            }
          )
        } else {
          JsArray(
            regimeSpecificDetails.map { case (key, value) =>
              Json.obj(
                "paramSequence" -> "01",
                "paramName"     -> key,
                "paramValue"    -> value
              )
            } ++
              breakdownEntries.zipWithIndex.flatMap { case (x, i) =>
                x.map { case (key, value) =>
                  Json.obj(
                    "paramSequence" -> "%02d".format(i + 1), // iterator needs to ascend from 01
                    "paramName"     -> key,
                    "paramValue"    -> value
                  )
                }
              }
          )
        }
      Json.obj(
        "receivedAt" -> ZonedDateTime
          .now()
          .truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
          .format(DateTimeFormatter.ISO_INSTANT),
        "periodFrom"     -> period.start,
        "periodTo"       -> period.end,
        "returnsDetails" -> Json.obj(
          "isrScenario"           -> "ZDS1",
          "regimeSpecificDetails" -> regimeSpecificJson
        )
      )
    }
  }
}
