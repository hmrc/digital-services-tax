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
import shapeless.tag.@@
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.digitalservicestax.connectors.TaxEnrolmentConnector
import uk.gov.hmrc.digitalservicestax.data
import uk.gov.hmrc.digitalservicestax.data.{DSTRegNumber, InternalId}

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

  private val dstRegConf: String = configuration.get[String]("DST_REGISTRATION_NUMBER")
  private val internalIdConf     = configuration.get[String]("CUST_ID")
  private val subscriptionId     = configuration.get[String]("USER_SUB")

  private val dstReg: String @@ data.DSTRegNumber.Tag = DSTRegNumber(dstRegConf)
  private val internalId                              = InternalId(internalIdConf)

  logger.warn("\nSEARCHING PENDING PENDING CALLBACKS FOR CUSTOMER WITH INTERNAL ID\n")

  db.pendingCallbacks.reverseLookup(internalId).foreach { optFormBundleNumber =>
    optFormBundleNumber.foreach { formBundleNumber =>
      logger.warn(s"\nFOUND FORM BUNDLE NUMBER: $formBundleNumber")
    }

    logger.warn("\nNO ENTRY IN PENDING CALLBACKS FOR INTERNAL ID\n")
  }
}
