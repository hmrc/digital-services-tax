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
import scala.concurrent.ExecutionContext

@Singleton
class DstRegRemover @Inject() (configuration: Configuration, db: MongoPersistence)(implicit ec: ExecutionContext)
    extends StartUpChecks {

  val logger: Logger = Logger(this.getClass)

  logger.warn("\n<<>><<>><<>><<>><<>><<>><<>>DST REG CHECKER RUNNING<<>><<>><<>><<>><<>><<>><<>>\n")

  val dstRegConfOne: Option[String]   = configuration.getOptional[String]("DST_REGISTRATION_NUMBER_ENC_ONE")
  val dstRegConfTwo: Option[String]   = configuration.getOptional[String]("DST_REGISTRATION_NUMBER_ENC_TWO")
  val dstRegConfThree: Option[String] = configuration.getOptional[String]("DST_REGISTRATION_NUMBER_ENC_THREE")

  if (dstRegConfOne.isEmpty || dstRegConfTwo.isEmpty || dstRegConfThree.isEmpty) {
    logger.info("\n<<>><<>><<>><<>><<>><<>><<>>ERROR READING VALUE<<>><<>><<>><<>><<>><<>><<>>\n")
  } else {
    val dstRegNumbersConf                                   = List(dstRegConfOne.head, dstRegConfTwo.head, dstRegConfThree.head)
    val dstRegNumbers: Seq[String @@ data.DSTRegNumber.Tag] = dstRegNumbersConf.map(DSTRegNumber(_))

    if (dstRegNumbers.isEmpty) {
      logger.info(
        "\n<<>><<>><<>><<>><<>><<>><<>>ERROR VALUE PROVIDED IS NOT A DST REGISTRATION NUMBER<<>><<>><<>><<>><<>><<>><<>>\n"
      )
    }

    /** Registrations
      */
    dstRegNumbers.foreach { dstRegNumber =>
      db.registrations.findByRegistrationNumber(dstRegNumber).foreach { optDstRegNum: Option[Registration] =>
        if (optDstRegNum.isEmpty) {
          logger.error(
            "\n<<>><<>><<>><<>><<>><<>><<>>ERROR NO REGISTRATION COULD BE FOUND FOR THE INTERNAL ID<<>><<>><<>><<>><<>><<>><<>>\n"
          )
        } else {
          logger.info(
            "\n<<>><<>><<>><<>><<>><<>><<>>DELETING REGISTRATION FOR A DST REGISTRATION NUMBER<<>><<>><<>><<>><<>><<>><<>>\n"
          )
          db.registrations.delete(dstRegNumber)
        }
      }
    }
  }
}
