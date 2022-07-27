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
import shapeless.tag.@@
import uk.gov.hmrc.digitalservicestax.data
import uk.gov.hmrc.digitalservicestax.data.{DSTRegNumber, Registration}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DstRegChecker @Inject()(configuration: Configuration, db: MongoPersistence)(implicit ec: ExecutionContext) extends StartUpChecks {

  val logger: Logger = Logger(this.getClass)

  logger.warn("\n<<>><<>><<>><<>><<>><<>><<>>DST REG CHECKER RUNNING<<>><<>><<>><<>><<>><<>><<>>\n")

  val dstRegNumberConf: Option[String] = configuration.getOptional[String]("DST_REGISTRATION_NUMBER_ENC")

  if (dstRegNumberConf.isEmpty) {
    logger.warn("\n<<>><<>><<>><<>><<>><<>><<>>ERROR READING VALUE<<>><<>><<>><<>><<>><<>><<>>\n")
  } else {
    val optDstRegNumber: Option[String @@ data.DSTRegNumber.Tag] = dstRegNumberConf.map(DSTRegNumber(_))

    if (optDstRegNumber.isEmpty) {
      logger.warn("\n<<>><<>><<>><<>><<>><<>><<>>ERROR VALUE PROVIDED IS NOT A DST REGISTRATION NUMBER<<>><<>><<>><<>><<>><<>><<>>\n")
    }

    val dstRegNumber: String @@ data.DSTRegNumber.Tag = optDstRegNumber.head

    /**
     * Registrations
     */
    val effOptReg: Future[Option[Registration]] = db.registrations.findByDstReg(dstRegNumber)

    effOptReg.map { optReg =>
      if (optReg.isEmpty) {
        logger.warn("\n<<>><<>><<>><<>><<>><<>><<>>ERROR NO REGISTRATION COULD BE FOUND FOR THE INTERNAL ID<<>><<>><<>><<>><<>><<>><<>>\n")
      }
    }
  }
}
