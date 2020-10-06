/*
 * Copyright 2020 HM Revenue & Customs
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
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent._

@Singleton
class RegistrationsController @Inject()(
  val authConnector: AuthConnector,
  val runModeConfiguration: Configuration,
  val runMode: RunMode,
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

  val log: Logger = Logger(this.getClass)
  val serviceConfig = new ServicesConfig(runModeConfiguration, runMode)

  implicit val ec: ExecutionContext = cc.executionContext
     
  def submitRegistration(): Action[JsValue] = loggedIn.async(parse.json) { implicit request =>
    withJsonBody[Registration](data => for {
      _      <- persistence.registrations(request.internalId) = data
      safeId <- data.companyReg.safeId.fold(rosmConnector.getSafeId(data).map{_.get})(_.pure[Future])
      reg    <- (
        // these steps are combined for auditing purposes
        for {
          r  <- registrationConnector.send(
            idType   = if (data.companyReg.useSafeId) "safe" else "utr",
            idNumber = if (data.companyReg.useSafeId) {safeId} else {
              data.companyReg.utr.getOrElse(getUtrFromAuth(request.enrolments).get)
            },
            data,
            request.providerId
          )
          _  <- persistence.pendingCallbacks.update(r.formBundleNumber, request.internalId)
          _  <- taxEnrolmentConnector.subscribe(safeId, r.formBundleNumber)
        } yield r
      ).auditError  (_ => AuditingHelper.buildRegistrationAudit(data, request.providerId, None, "ERROR"))
       .auditSuccess(r => AuditingHelper.buildRegistrationAudit(data, request.providerId, Some(r.formBundleNumber), "SUCCESS"))
      _      <- emailConnector.sendSubmissionReceivedEmail(
        data.contact,
        data.companyReg.company.name,
        data.ultimateParent
      )
    } yield (Ok(JsNull))
    )
  }

  def lookupRegistration(): Action[AnyContent] = loggedIn.async { implicit request =>
    persistence.registrations.get(request.internalId).flatMap {
      case Some(r) if r.registrationNumber.isDefined => Ok(Json.toJson(r)).pure[Future]
      case Some(r) =>
        // the registration may not have completed yet, or the callback was unable to return
        // check to see if there is a enrolment record.
        (for {
          formBundle            <- OptionT(persistence.pendingCallbacks.reverseLookup(request.internalId))
          subscription          <- OptionT.liftF(taxEnrolmentConnector.getSubscription(formBundle))
          processedRegistration <- subscription match {
            case s@TaxEnrolmentsSubscription(Some(_), _, "SUCCEEDED", _) => for {
              dstNum   <- OptionT.fromOption[Future](s.getDSTNumber)
              updatedR <- OptionT.liftF(persistence.pendingCallbacks.process(formBundle, dstNum))
              period   <- OptionT.liftF(returnConnector.getNextPendingPeriod(dstNum))
              _        <- OptionT.liftF(
                            emailConnector
                              .sendConfirmationEmail(
                                updatedR.contact,
                                updatedR.companyReg.company.name,
                                updatedR.ultimateParent.fold(CompanyName("unknown")){x => x.name},
                                dstNum,
                                period
                              )
                          )
              _        <- OptionT.liftF(
                            auditing
                              .sendExtendedEvent(
                                AuditingHelper.buildCallbackAudit(
                                  CallbackNotification("SUCCEEDED", None),
                                  request.uri,
                                  formBundle,
                                  "SUCCESS",
                                  dstNum.some)
                              )
                          )
            } yield updatedR

            case TaxEnrolmentsSubscription(identifiers, _, state, errors) =>
              // we have a state other than SUCCEEDED
              Logger.warn(
                s"state: $state, " +
                s"errors: ${errors.getOrElse("none reported")}, " +
                s"identifiers: ${identifiers.fold("none")(_=> "some")}")
              Logger.warn("attempting TE subscribe again")
              // attempt to subscribe again
              (r.companyReg.safeId match {
                case None => rosmConnector.getSafeId(r)
                case fo => Future.successful(fo)
              }) map { x: Option[data.SafeId] =>
                x.map { safeId => 
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

            case _ => OptionT.some[Future](r)
          }
        } yield processedRegistration).fold {
          Logger.info("unable to get processed registration")
          NotFound(JsNull)
        }(y => Ok(Json.toJson(y)))
      case None => {
        Logger.warn("no pending registration")
        NotFound.pure[Future]
      }
    }
  }

}
