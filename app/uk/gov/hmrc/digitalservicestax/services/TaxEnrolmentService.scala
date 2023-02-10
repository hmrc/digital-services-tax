/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.Logging
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors.TaxEnrolmentConnector
import uk.gov.hmrc.digitalservicestax.data.Registration
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class TaxEnrolmentService @Inject() (appConfig: AppConfig, taxEnrolmentConnector: TaxEnrolmentConnector, persistence: MongoPersistence)
    extends Logging {

  def getDSTRegistration(
    groupId: Option[String]
  )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[Option[Registration]] = {
    (groupId, appConfig.dstNewSolutionFeatureFlag) match {
      case (Some(groupId), true) =>
        taxEnrolmentConnector
          .getSubscriptionByGroupId(groupId)
          .flatMap { taxEnrolmentSubscription =>
            if (taxEnrolmentSubscription.errorResponse.isDefined) {
              logger.error(
                s"Error response received while getting Tax enrolment subscription by groupId: " +
                  s"${taxEnrolmentSubscription.errorResponse.getOrElse("")}"
              )
              Future.successful(None)
            } else {

              taxEnrolmentSubscription.getDSTNumberWithSucceededState match {
                case Some(dstRegNumber) =>
                  persistence.registrations.getByRegistrationNumber(dstRegNumber)
                case _                  => Future(None)
              }
            }
          }
          .recoverWith { case ex: Exception =>
            logger.error(
              s"Unexpected error response received while getting Tax enrolment subscription by groupId: " +
                s"${ex.getMessage}"
            )
            Future.successful(None)
          }
      case _             => Future(None)
    }
  }
}
