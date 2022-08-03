/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.{Configuration, Logger}
import uk.gov.hmrc.digitalservicestax.data.{DSTRegNumber, InternalId, Registration}
import uk.gov.hmrc.digitalservicestax.services.MongoPersistence.RegWrapper

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

@Singleton
class DstRegChecker @Inject()(configuration: Configuration, db: MongoPersistence)(implicit ec: ExecutionContext) extends StartUpChecks {

  val logger: Logger = Logger(this.getClass)

  logger.info("DST REG CHECKER RUNNING")

  val dstRegNumberConf: Option[String] = configuration.getOptional[String]("DST_REGISTRATION_NUMBER_ENC")

  if (dstRegNumberConf.isEmpty) {
    logger.info("ERROR READING VALUE")
  } else {
    Try(dstRegNumberConf.map(DSTRegNumber(_))) match {
      case Success(dstRegNumber) =>
        logger.info("DST REGISTRATION IS A VALID ONE")
        db.registrations.findByDstReg(dstRegNumber.head).map { optReg: Option[RegWrapper] =>
          if (optReg.isEmpty) {
            logger.info("ERROR NO REGISTRATION COULD BE FOUND FOR THE DST REGISTRATION NUMBER IN THE CONF")
          } else {
            optReg.foreach(regWrapper => logger.info(s"FOUND REGISTRATION FOR THE USER, THE INTERNAL ID IS: ${regWrapper.session}"))

          }
        }
      case Failure(_) => logger.info("DST REGISTRATION IS NOT A VALUE FORMAT")
    }
  }
}
