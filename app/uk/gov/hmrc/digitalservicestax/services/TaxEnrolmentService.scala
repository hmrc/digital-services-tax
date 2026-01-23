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

package uk.gov.hmrc.digitalservicestax.services

import play.api.Logging
import play.api.mvc.Result
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors.TaxEnrolmentConnector
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc.Results.{NotFound, Ok}

class TaxEnrolmentService @Inject() (
  appConfig: AppConfig,
  taxEnrolmentConnector: TaxEnrolmentConnector,
  persistence: MongoPersistence
) extends Logging {

  def getPendingDSTRegistration(
    groupId: Option[String]
  )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[Result] =
    (groupId, appConfig.dstNewSolutionFeatureFlag) match {
      case (Some(groupId), true) =>
        taxEnrolmentConnector
          .getPendingSubscriptionByGroupId(groupId)
          .flatMap {
            case Some(taxEnrolmentSubscription) =>
              if (taxEnrolmentSubscription.errorResponse.isDefined) {
                logger.error(
                  s"Error response received while getting Tax enrolment subscription by groupId: " +
                    s"${taxEnrolmentSubscription.errorResponse.getOrElse("")}"
                )
                Future.successful(NotFound)
              } else Future.successful(Ok)
            case _                              =>
              logger.info(s"Failed to get the response from Tax enrolment subscription by groupId")
              Future.successful(NotFound)
          }
          .recoverWith { case ex: Exception =>
            logger.error(
              s"Unexpected error response received while getting Tax enrolment subscription by groupId: " +
                s"${ex.getMessage}"
            )
            Future.successful(NotFound)
          }
      case _                     => Future(NotFound)
    }
}
