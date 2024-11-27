/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.digitalservicestax.services

import play.api.libs.json.{Json, OWrites}
import play.api.{Configuration, Logger}
import play.mvc.Http
import shapeless.tag.@@
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.digitalservicestax.connectors.TaxEnrolmentConnector
import uk.gov.hmrc.digitalservicestax.data
import uk.gov.hmrc.digitalservicestax.data.{DSTRegNumber, Email, InternalId, SafeId}
import uk.gov.hmrc.digitalservicestax.services.MongoPersistence.RegWrapper
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, RequestId}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class DstRegUpdater @Inject() (
  configuration: Configuration,
  db: MongoPersistence,
  taxEnrolmentConnector: TaxEnrolmentConnector
)(implicit ec: ExecutionContext)
    extends StartUpChecks {

  val logger: Logger                                   = Logger(this.getClass)
  implicit val credentialsWrites: OWrites[Credentials] = Json.writes[Credentials]

  logger.warn("\nDST REG UPDATER RUNNING\n")

  private val dstRegConf: String                      = configuration.get[String]("DST_REGISTRATION_NUMBER")
  private val internalIdConf                          = configuration.get[String]("CUST_ID")
  private val dstReg: String @@ data.DSTRegNumber.Tag = DSTRegNumber(dstRegConf)
  private val internalId                              = InternalId(internalIdConf)

  logger.warn("\nSEARCHING PENDING REGISTRATIONS FOR CUSTOMER WITH INTERNAL ID\n")

  db.registrations.get(InternalId(internalId)).foreach { optReg =>
    if (optReg.isEmpty) {
      logger.error("\nNO USER FOUND FOR GIVEN INTERNAL ID\n")
    } else {
      logger.warn("\nFOUND USER FOR INTERNAL ID, CHECKING IF THE DST REFERENCE IS SET FOR THE USER'S REGISTRATION\n")
      val regWrapper = optReg.head

      if (regWrapper.registrationNumber.isEmpty) {
        logger.error("\nDST REFERENCE NUMBER IS NOT SET FOR USER, SETTING IT\n")
        db.registrations.update(internalId, regWrapper.copy(registrationNumber = Some(dstReg)))
      }

      regWrapper.registrationNumber.foreach { currDstRegNum =>
        if (currDstRegNum.!=(dstReg)) {
          logger.error(s"THE CURRENT DST REGISTRATION NUMBER IS NOT WHAT WE EXPECTED: ${currDstRegNum
              .takeRight(2)}, DATE LIABLE IS: ${regWrapper.dateLiable}")
        }
      }

      if (regWrapper.companyReg.safeId.isEmpty) {
        logger.error("\nSAFE ID NOT FOUND IN DATA SETTING IT\n")
        val safeId = SafeId(configuration.get[String]("USER_SAFE_ID"))
        db.registrations
          .update(internalId, regWrapper.copy(companyReg = regWrapper.companyReg.copy(safeId = Some(safeId))))
      }

      logger.warn("\nRE-SUBSCRIBING CUSTOMER TO TAX-ENROLMENTS\n")

      implicit val headers: HeaderCarrier = buildHeaders(
        HeaderCarrier(
          authorization = Some(Authorization("Bearer " + configuration.get[String]("USER_TOKEN"))),
          requestId = Some(RequestId(UUID.randomUUID().toString))
        ),
        internalId
      )

      taxEnrolmentConnector.subscribe(regWrapper.companyReg.safeId.head, internalId).foreach {
        case httpResponse if httpResponse.status == Http.Status.NO_CONTENT =>
          logger.warn("\nEXPECTED SUCCESSFUL RESPONSE RETURNED FROM TAX ENROLMENTS UPDATER STOPPING\n")
        case httpResponse                                                  =>
          logger.error(
            s"\nUNKNOWN RESPONSE RECEIVED FROM TAX ENROLMENTS UPDATER ABORTING\n STATUS IS: ${httpResponse.status}\n BODY IS: ${httpResponse.body}"
          )
      }
    }
  }

  private def buildHeaders(headers: HeaderCarrier, internalId: InternalId): HeaderCarrier =
    headers.withExtraHeaders(
      ("groupIdentifier", configuration.get[String]("GROUP_ID")),
      (
        "credentials",
        Json.toJson(Credentials(configuration.get[String]("PROVIDER_ID"), "GovernmentGateway")).toString()
      ),
      ("affinityGroup", "Organisation"),
      ("email", configuration.get[String]("USER_EMAIL")),
      ("internalId", internalId)
    )

}
