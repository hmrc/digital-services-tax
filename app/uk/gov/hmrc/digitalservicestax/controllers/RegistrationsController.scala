/*
 * Copyright 2021 HM Revenue & Customs
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
package controllers

import cats.data.OptionT
import cats.implicits._


import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.digitalservicestax.actions._
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors._
import uk.gov.hmrc.digitalservicestax.data.BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.data.{percentFormat => _, _}
import uk.gov.hmrc.digitalservicestax.services.{AuditingHelper, MongoPersistence}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.http.HttpClient

import scala.concurrent._

@Singleton
class RegistrationsController @Inject()(
  val authConnector: AuthConnector,
  val runModeConfiguration: Configuration,
  appConfig: AppConfig,
  cc: ControllerComponents,
  registrationConnector: RegistrationConnector,
  rosmConnector: RosmConnector,
  taxEnrolmentConnector: TaxEnrolmentConnector,
  emailConnector: EmailConnector,
  persistence: MongoPersistence,
  val auditing: AuditConnector,
  loggedIn: LoggedInAction,
  registered: RegisteredOrPending,
  returnConnector: ReturnConnector,
  val http: HttpClient,
  val servicesConfig: ServicesConfig
) extends BackendController(cc) with AuthorisedFunctions with DesHelpers with AuditWrapper{

  val logger: Logger = Logger(this.getClass)
  val serviceConfig = new ServicesConfig(runModeConfiguration)

  implicit val ec: ExecutionContext = cc.executionContext
     
  def submitRegistration(): Action[JsValue] = loggedIn.async(parse.json) { implicit request =>
    withJsonBody[Registration](data => for {
      _      <- persistence.registrations.update(request.internalId, data)
      safeId <- data.companyReg.safeId.fold(rosmConnector.getSafeId(data).map{_.get})(_.pure[Future])
      reg    <- (
        // these steps are combined for auditing purposes
        for {
          r  <- registrationConnector.send(
            idType   = if (data.companyReg.useSafeId) "safe" else "utr",
            idNumber = if (data.companyReg.useSafeId) {safeId} else {
              data.companyReg.utr.getOrElse(
                getUtrFromAuth(
                  request.enrolments
                ).getOrElse(throw new RuntimeException("Rosm not retrieved safeId and not UTR"))
              )
            },
            data,
            request.providerId
          )
          _  <- persistence.pendingCallbacks(r.formBundleNumber) = request.internalId
          _  <- taxEnrolmentConnector.subscribe(safeId, r.formBundleNumber)
        } yield r
      ).auditError  (_ => AuditingHelper.buildRegistrationAudit(data, request.providerId, None, "ERROR"))
       .auditSuccess(r => AuditingHelper.buildRegistrationAudit(data, request.providerId, Some(r.formBundleNumber), "SUCCESS"))
      _      <- emailConnector.sendSubmissionReceivedEmail(
        data.contact,
        data.companyReg.company.name,
        data.ultimateParent
      )
    } yield Ok(Json.toJson(reg))
    )
  }

  /** The registration may not have completed yet, or the callback was unable to return.
    * Check to see if there is a enrolment record. 
    */
  def attemptRegistrationFix(r: Registration)(implicit request: LoggedInRequest[AnyContent], hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Registration]] =
  {
    logger.warn(s"Enrolments from Auth: ${request.enrolments.enrolments.map(_.key).mkString(", ")}")
    (for {
      formBundle            <- OptionT(persistence.pendingCallbacks.reverseLookup(request.internalId))
      subscription          <- OptionT.liftF(taxEnrolmentConnector.getSubscription(formBundle))
      processedRegistration <- subscription match {
            case s@TaxEnrolmentsSubscription(_, _, "SUCCEEDED", _) =>
              logger.info("fetched a SUCCEEDED subscription. About to process and audit.")
              for {
                dstNum <- OptionT.fromOption[Future](s.getDSTNumber)
                updatedR <- OptionT.liftF(persistence.pendingCallbacks.process(formBundle, dstNum))
                period   <- OptionT.liftF(returnConnector.getNextPendingPeriod(dstNum))
                _        <- OptionT.liftF(emailConnector.sendConfirmationEmail(
                  updatedR.contact,
                  updatedR.companyReg.company.name,
                  updatedR.ultimateParent.fold(CompanyName("unknown")){x => x.name},
                  dstNum,
                  period
                ).auditSuccess(_ => AuditingHelper.buildCallbackAudit(
                  CallbackNotification("SUCCEEDED", None),
                  request.uri,
                  formBundle,
                  "SUCCESS",
                  s.getDSTNumber)))
              } yield updatedR
              
            case TaxEnrolmentsSubscription(identifiers, _, state, errors) =>
              // we have a state other than SUCCEEDED
              logger.warn(
                s"state: $state, " +
                s"errors: ${errors.getOrElse("none reported")}, " +
                s"identifiers: ${if (identifiers.isEmpty) "none" else "some"}")
              logger.warn("attempting TE subscribe again")
              // attempt to subscribe again
              r.companyReg.safeId.fold(rosmConnector.getSafeId(r))(_.some.pure[Future]).map {
                _.map { safeId =>
                  taxEnrolmentConnector.subscribe(
                    safeId,
                    formBundle
                  ).auditError(_ => AuditingHelper.buildRegistrationAudit(
                    r, request.providerId, None, "ERROR"
                  )).auditSuccess(_ => AuditingHelper.buildRegistrationAudit(
                    r, request.providerId, formBundle.some, "SUCCESS"
                  )).map(_ => ())
                }
              }
              OptionT.some[Future](r)
          }
        } yield processedRegistration).fold {
          logger.warn("unable to get processed registration")
          none[Registration]
        }(_.some)
  }

  def lookupRegistration(): Action[AnyContent] = loggedIn.async { implicit request =>
    for {
      a <- persistence.registrations.get(request.internalId)
      b <- persistence.pendingCallbacks.reverseLookup(request.internalId)
    } yield (a, b) match {
      case (Some(r), _) if r.registrationNumber.isDefined =>
        Ok(Json.toJson(r))
      case (Some(r), p) if p.nonEmpty =>
        logger.info(s"pending registration for ${request.internalId}")
        Ok(Json.toJson(r))
      case _ =>
        logger.warn("no pending registration")
        NotFound
    }
  }

}
