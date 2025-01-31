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
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.digitalservicestax.connectors.TaxEnrolmentConnector
import uk.gov.hmrc.digitalservicestax.data.FormBundleNumber

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

  private val formBundleNumber = configuration.get[String]("FORM_BUNDLE_NUMBER")

  logger.warn("\nSEARCHING PENDING CALLBACKS FOR CUSTOMER WITH FORM BUNDLE NUMBER\n")

  db.pendingCallbacks.get(FormBundleNumber(formBundleNumber)).foreach { optInternalId =>
    if (optInternalId.nonEmpty) {
      logger.warn("CUSTOMER STILL EXISTS IN PENDING CALLBACKS & REQUIRES FURTHER PROCESSING")
      optInternalId.foreach { dbInternalId =>
        db.registrations.apply(dbInternalId).foreach { existingRegistration =>
          if (existingRegistration.registrationNumber.isEmpty) {
            logger.warn(
              "THE CUSTOMER REGISTRATION DOESN'T CONTAIN THE dst REGISTRATION nUMBER & REQUIRES FURTHER PROCESSING"
            )
          }
        }
      }
    } else {
      logger.info("CUSTOMER HAS BEEN PROCESSED & REMOVED FROM PENDING CALLBACKS")
    }
  }
}
