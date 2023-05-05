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

package uk.gov.hmrc.digitalservicestax
package actions

import play.api.{Logger, Logging}
import play.api.mvc.Results.Forbidden
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.controllers.RegistrationsController
import uk.gov.hmrc.digitalservicestax.data._
import uk.gov.hmrc.digitalservicestax.services.{MongoPersistence, TaxEnrolmentService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class Registered @Inject() (
  persistence: MongoPersistence,
  appConfig: AppConfig
)(implicit executionContext: ExecutionContext)
    extends RegisteredOrPending(persistence, appConfig) {

  override def refine[A](
    request: LoggedInRequest[A]
  ): Future[Either[Result, RegisteredRequest[A]]] = super.refine(request)
}

class RegisteredOrPending @Inject() (
  persistence: MongoPersistence,
  appConfig: AppConfig
)(implicit val executionContext: ExecutionContext)
    extends ActionRefiner[LoggedInRequest, RegisteredRequest] {

  val logger = Logger(getClass)

  def refine[A](
    request: LoggedInRequest[A]
  ): Future[Either[Result, RegisteredRequest[A]]] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    getRegistration(request, appConfig).map {
      case Some(reg) if reg.registrationNumber.isDefined =>
        Right(RegisteredRequest(reg, request))
      case _                                             =>
        Left(Forbidden("User is not registered"))
    }
  }

  def getRegistration[A](
    request: LoggedInRequest[A],
    appConfig: AppConfig
  )(implicit hc: HeaderCarrier): Future[Option[Registration]] =
    if (appConfig.dstNewSolutionFeatureFlag) {
      request.enrolments.enrolments
        .find(enrolment => enrolment.key == "HMRC-DST-ORG" && enrolment.isActivated)
        .map(_.getIdentifier("DSTRefNumber") match {
          case Some(dstRegNumber) =>
            persistence.registrations.findByRegistrationNumber(DSTRegNumber(dstRegNumber.value))
          case _                  =>
            logger.info(s"DST enrolment does not exists for user")
            Future.successful(None)
        })
        .getOrElse(Future.successful(None))
        .recoverWith { case ex: Exception =>
          logger.error(
            s"Unexpected error response received while getting Tax enrolment subscription by groupId: " +
              s"${ex.getMessage}"
          )
          Future.successful(None)
        }
    } else {
      persistence.registrations.get(request.internalId) flatMap {
        case Some(registration) => Future.successful(Some(registration))
        case _                  => Future.successful(None)
      }
    }
}

case class RegisteredRequest[A](
  registration: Registration,
  authRequest: LoggedInRequest[A]
) extends WrappedRequest(authRequest.request)

class LoggedInAction @Inject() (
  mcc: MessagesControllerComponents,
  appConfig: AppConfig,
  val authConnector: AuthConnector
)(implicit val executionContext: ExecutionContext)
    extends ActionBuilder[LoggedInRequest, AnyContent]
    with ActionRefiner[Request, LoggedInRequest]
    with AuthorisedFunctions
    with Logging {

  override def refine[A](request: Request[A]): Future[Either[Result, LoggedInRequest[A]]] = {
    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequest(
        request
      )

    val retrieval = authorisedEnrolments and internalId and credentials and groupIdentifier

    authorised(AuthProviders(GovernmentGateway)).retrieve(retrieval) { case enrolments ~ id ~ creds ~ groupId =>
      val providerId = creds.map(_.providerId)
      Future.successful(
        (id.map(InternalId.of), providerId) match {
          case (Some(Some(internalId)), Some(provider)) =>
            if (appConfig.dstNewSolutionFeatureFlag) {
              logger.warn("DST user logged in")
            }
            Right(LoggedInRequest(internalId, enrolments, provider, groupId, request))
          case (_, None)                                => Left(Forbidden("No provider ID"))
          case (Some(None), _)                          => Left(Forbidden("Invalid Internal ID"))
          case (None, _)                                => Left(Forbidden("No internal ID"))
        }
      )
    }
  }

  override def parser: BodyParser[AnyContent] = mcc.parsers.anyContent
}

case class LoggedInRequest[A](
  internalId: InternalId,
  enrolments: Enrolments,
  providerId: String,
  groupId: Option[String],
  request: Request[A]
) extends WrappedRequest(request) {

  lazy val utr: Option[UTR] = enrolments
    .getEnrolment("IR-CT")
    .orElse(enrolments.getEnrolment("IR-SA"))
    .flatMap(_.getIdentifier("UTR").map(x => UTR(x.value)))
}
