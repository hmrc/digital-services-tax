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
import java.time.{Period => _, _}, format.DateTimeFormatter
import enumeratum._, values._
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

  implicit val registrationWriter = new Writes[Registration] {
     implicit val trueFalseIndicatorType = new Writes[Boolean] {
       def writes(b: Boolean): JsValue = if (b) JsString("1") else JsString("0")
     }

     def strDate(d: LocalDate): String =
       d.format(format.DateTimeFormatter.BASIC_ISO_DATE)

     def writes(o: Registration): JsValue = {
       import o._

       val contactAddress = alternativeContact getOrElse company.address

       val data = Json.obj(
         "registrationDetails" -> Json.obj(
           "isrScenario" -> "ZDS2",
           "commonDetails" -> Json.obj(
             "legalEntity" -> Json.obj(
 	      "dateOfApplication" -> LocalDate.now.toString, // should this always be todays date?
 	      "taxStartDate" -> dateLiable // should this always be todays date?
             ),
             "customerIdentificationNumber" -> Json.obj(
               //	      "custIDNumber" -> "???", // what should this be?
 	      "noIdentifier" -> false,  // Customer Identifier Indicator where 1: True, 0: False. Expected to always be False for MDTP submissions
//               "title" -> customer.title,
               "custFirstName" -> contact.forename,
               "custLastName" -> contact.surname
//               "custDOB" -> customer.dateOfBirth
 //	      "organisationName" -> customer.organisationName,
             ),
             "businessContactDetails" -> Json.obj(
               "addressInputModeIndicator" -> "2",
               "addressLine1" -> contactAddress.line1,
               "addressLine2" -> Some(contactAddress.line2).filter(_.nonEmpty),
               "addressLine3" -> Some(contactAddress.line3).filter(_.nonEmpty),
               "addressLine4" -> Some(contactAddress.line4).filter(_.nonEmpty),
               "postCode" -> Some(contactAddress.postalCode).filter(_.nonEmpty),
               "addressNotInUK" -> contactAddress.isInstanceOf[ForeignAddress],
               "nonUKCountry" -> Some(contactAddress).collect{ case f: ForeignAddress => f.countryCode },
               "email" -> Some(contact.email).filter(_.nonEmpty),
               "telephoneNumber" -> Some(contact.phoneNumber).filter(_.nonEmpty)
             )
           ),
           "regimeSpecificDetails" -> ListMap[String, String](
             "A_DST_PRIM_NAME" -> {contact.forename + " " + contact.surname},
             "A_DST_PRIM_TELEPHONE" -> contact.phoneNumber,
             "A_DST_PRIM_EMAIL" -> contact.email,
             "A_DST_GLOBAL_NAME" -> company.name,
             "A_DATA_ORIGIN" -> "1",
             "A_DST_PERIOD_END_DATE" -> strDate(accountingPeriodEnd),
             "A_TAX_START_DATE" -> strDate(dateLiable),
             "A_BUS_ADR_LINE_5" -> contactAddress.line5,
             "A_CORR_ADR_LINE_1" -> company.address.line1,                                                
             "A_CORR_ADR_LINE_2" -> company.address.line2,                                    
             "A_CORR_ADR_LINE_3" -> company.address.line3,                        
             "A_CORR_ADR_LINE_4" -> company.address.line4,            
             "A_CORR_ADR_LINE_5" -> company.address.line5,
             "A_CORR_ADR_POST_CODE" -> company.address.postalCode,
             "A_CORR_ADR_COUNTRY_CODE" -> company.address.countryCode
//             "A_DST_GLOBAL_ID" -> ultimateOwner.reference
           )
         )
       )

       purgeNull(data)
     }
  }

  def returnRequestWriter(dstRegNo: String, period: Period, isAmend: Boolean = false) = new Writes[Return] {
    def writes(o: Return): JsValue = {
      import o._

      def bool(in: Boolean): String = if(in) "X" else " "

      import Activity._
      val activityEntries: Seq[(String, String)] =
        alternateCharge.toList flatMap { case (activityType,v) =>

          val key = activityType match {
            case SocialMedia => "SOCIAL"
            case SearchEngine => "SEARCH"
            case OnlineMarketplace => "MARKET"
          }

          List(
            s"DST_${key}_CHARGE_PROVISION" -> bool(true),
            s"DST_${key}_LOSS" -> bool(v == 0),
            s"DST_${key}_OP_MARGIN" -> v.toString
          )
        }

      val subjectEntries: Seq[(String, String)] =
        Activity.values.map{ activityType =>
          val key = activityType match {
            case SocialMedia => "SOCIAL"
            case SearchEngine => "SEARCHENGINE"
            case OnlineMarketplace => "MARKETPLACE"
          }

          (s"DST_SUBJECT_${key}" -> bool(alternateCharge.isDefinedAt(activityType)))
        }

      val repaymentInfo: Seq[(String, String)] =
        repayment.fold(Seq.empty[(String, String)]){
          case RepaymentDetails(acctName, DomesticBankAccount(sortCode, acctNo, bsNo)) =>
            Seq[(String, String)](
              "BANK_NAME" -> acctName,  // Name of account CHAR40
              "BANK_NON_UK" -> bool(false),
//              "BANK_BSOC_NAME" -> bank.bankName, // Name of bank or building society CHAR40
              "BANK_SORT_CODE" -> sortCode, // Branch sort code CHAR6
              "BANK_ACC_NO" -> acctNo // Account number CHAR8
            ) ++ Some(bsNo).filter(_.nonEmpty).map { a => 
              "BUILDING_SOC_ROLE" -> a // Building Society reference CHAR20
            }.toSeq

          case RepaymentDetails(acctName, ForeignBankAccount(iban)) => Seq(
            "BANK_IBAN" -> iban, // IBAN if non-UK bank account CHAR34
            "BANK_NAME" -> acctName // Name of account CHAR40
          )
        }

      val breakdownEntries: Seq[(String, String)] = companiesAmount.toList flatMap { case (company, amt) =>
        Seq(
          "DST_GROUP_MEMBER" -> company.name, // Group Member Company Name CHAR40
          "DST_GROUP_MEM_ID" -> company.utr, // Company registration reference number (UTR) CHAR40
          "DST_GROUP_MEM_LIABILITY" -> amt.toString // DST liability amount per group member BETRW_KK
        )
      }

      val regimeSpecificDetails: Seq[(String, String)] = Seq(
        "REGISTRATION_NUMBER" -> dstRegNo, // MANDATORY ID Reference number ZGEN_FBP_REFERENCE
        "PERIOD_FROM" -> period.start.toString, // MANDATORY Period From  DATS
        "PERIOD_TO" -> period.start.toString, // MANDATORY Period To  DATS
        "DST_FIRST_RETURN" -> bool(!isAmend), // Is this the first return you have submitted for this company and this accounting period? CHAR1
        "DST_RELIEF" -> bool(crossBorderReliefAmount > 0), // Are you claiming relief for relevant cross-border transactions? CHAR1
        "DST_TAX_ALLOWANCE" -> allowanceAmount.toString, // What tax-free allowance is being claimed against taxable revenues? BETRW_KK
        "DST_GROUP_LIABILITY" -> totalLiability.toString, // MANDATORY Digital Services Group Total Liability BETRW_KK
        "DST_REPAYMENT_REQ" -> bool(repayment.isDefined), // Repayment for overpayment required? CHAR1
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
