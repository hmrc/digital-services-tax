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
import uk.gov.hmrc.digitalservicestax.backend_data.SubscriptionStatus
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors.{EnrolmentStoreProxyConnector, RegistrationConnector, RosmConnector, TaxEnrolmentConnector}
import uk.gov.hmrc.digitalservicestax.data.{DSTRegNumber, Registration, UTR}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class GetDstNumberFromEisService @Inject() (
  appConfig: AppConfig,
  rosmConnector: RosmConnector,
  registrationConnector: RegistrationConnector,
  persistence: MongoPersistence,
  taxEnrolmentConnector: TaxEnrolmentConnector,
  enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector
) extends Logging {

  private def getDstNumberFromEis(
    utr: UTR
  )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[Option[String]] =
    rosmConnector.retrieveROSMDetails(utr).flatMap {
      case Some(companyRegWrapper) =>
        companyRegWrapper.sapNumber match {
          case Some(sapNumber) =>
            registrationConnector.getSubscriptionStatus(sapNumber).map {
              case subscriptionStatusResponse
                  if subscriptionStatusResponse.idType == Some("ZDST")
                    && subscriptionStatusResponse.subscriptionStatus == SubscriptionStatus.Subscribed
                    && subscriptionStatusResponse.idValue.nonEmpty =>
                Some(subscriptionStatusResponse.idValue.get)
              case _ => None
            }
          case _               => Future.successful(None)
        }
      case _                       => Future.successful(None)
    }

  def getDstNumberAndActivateEnrolment(utr: UTR, groupId: String)(implicit
    hc: HeaderCarrier,
    executionContext: ExecutionContext
  ): Future[Option[Registration]] =
    getDstNumberFromEis(utr).flatMap {
      case Some(dstRegNumber) =>
        persistence.registrations.findByRegistrationNumber(DSTRegNumber(dstRegNumber)).flatMap {
          case Some(registration) =>
            taxEnrolmentConnector
              .isAllocateDstGroupEnrolmentSuccess(
                registration.companyReg.company.address,
                dstRegNumber
              )
              .flatMap {
                case true =>
                  enrolmentStoreProxyConnector.getDstRefFromGroupAssignedEnrolment(groupId).flatMap {
                    case Some(dstRegNumberFromEacd) if dstRegNumber == dstRegNumberFromEacd =>
                      Future.successful(Some(registration))
                    case Some(dstRegNumberFromEacd) if dstRegNumber != dstRegNumberFromEacd =>
                      logger.error("DstRegNumber from ETMP and EACD are different")
                      Future.successful(None)
                    case _                                                                  => Future.successful(None)
                  }
                case _    => Future.successful(None)
              }
          case _                  => Future.successful(None)
        }
      case _                  => Future.successful(None)
    }
}
