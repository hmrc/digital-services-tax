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
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.digitalservicestax.connectors.TaxEnrolmentConnector
import uk.gov.hmrc.digitalservicestax.data.{DSTRegNumber, Email, SafeId}
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

  private val optDstReg: Option[String] = configuration.getOptional[String]("DST_REGISTRATION_NUMBER")
  private val dstReg: String            = optDstReg.head

  logger.warn("\nSEARCHING BY SAFE ID\n")
  db.registrations.findBySafeId(SafeId(configuration.get[String]("USER_SAFE_ID"))).foreach { optRegWrapperBySafeId =>
    if (optRegWrapperBySafeId.isEmpty) {
      logger.error("\nERROR NO REGISTRATION COULD BE FOUND FOR THE SAFE ID\n")

      logger.warn("\nSEARCHING BY EMAIL\n")
      db.registrations.findByEmail(Email(configuration.get[String]("USER_EMAIL"))).foreach { optRegWrapperByEmail =>
        if (optRegWrapperByEmail.isEmpty) {
          logger.error("\nERROR NO REGISTRATION COULD BE FOUND FOR THE EMAIL\n")

          db.registrations.findWrapperByRegistrationNumber(DSTRegNumber(dstReg)).foreach { optRegWrapperByDstRegNum =>
            if (optRegWrapperByDstRegNum.isEmpty) {
              logger.error("\nERROR NO REGISTRATION COULD BE FOUND FOR THE DST REGISTRATION NUMBER\n")
            } else {
              subscribe(optRegWrapperByDstRegNum.head)
            }
          }
        } else {
          subscribe(optRegWrapperByEmail.head)
        }
      }
    } else {
      subscribe(optRegWrapperBySafeId.head)
    }
  }

  private def subscribe(regWrapper: RegWrapper): Unit = {
    logger.warn("\nCHECKING FOR SAFE ID IN RETRIEVED DATA\n")
    if (regWrapper.data.companyReg.safeId.isEmpty) {
      logger.error("\nSAFE ID NOT FOUND IN DATA SETTING IT\n")
      val safeId = SafeId(configuration.get[String]("USER_SAFE_ID"))
      regWrapper
        .copy(data = regWrapper.data.copy(companyReg = regWrapper.data.companyReg.copy(safeId = Some(safeId))))
    } else {
      logger.warn("\nSAFE ID FOUND PROCEEDING WITH TAX ENROLMENT\n")
      implicit val headers: HeaderCarrier = buildHeaders(
        HeaderCarrier(
          authorization = Some(
            Authorization(
              "Bearer " + configuration.get[String]("USER_TOKEN")
            )
          ),
          requestId = Some(RequestId(UUID.randomUUID().toString))
        ),
        regWrapper.session
      )
      taxEnrolmentConnector.subscribe(regWrapper.data.companyReg.safeId.head, regWrapper.session).foreach {
        case httpResponse if httpResponse.status == Http.Status.NO_CONTENT =>
          logger.warn("\nEXPECTED SUCCESSFUL RESPONSE RETURNED FROM TAX ENROLMENTS UPDATER STOPPING\n")
        case httpResponse                                                  =>
          logger.error(
            s"\nUNKNOWN RESPONSE RECEIVED FROM TAX ENROLMENTS UPDATER ABORTING\n STATUS IS: ${httpResponse.status}\n BODY IS: ${httpResponse.body}"
          )
      }
    }
  }

  private def buildHeaders(headers: HeaderCarrier, internalId: String): HeaderCarrier =
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
