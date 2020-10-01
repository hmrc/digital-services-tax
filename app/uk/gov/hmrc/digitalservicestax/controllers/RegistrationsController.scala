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

import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders, AuthorisedFunctions}
import uk.gov.hmrc.digitalservicestax.data._
import BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.actions._
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors._
import uk.gov.hmrc.digitalservicestax.backend_data.{RegistrationResponse, RosmRegisterWithoutIDRequest}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors.{RegistrationConnector, RosmConnector, TaxEnrolmentConnector}
import uk.gov.hmrc.digitalservicestax.data.{percentFormat => _, _}
import uk.gov.hmrc.digitalservicestax.services.{AuditingHelper, MongoPersistence}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import java.time.LocalDateTime

import cats.data.OptionT

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
  auditing: AuditConnector,
  loggedIn: LoggedInAction,
  registered: RegisteredOrPending,
  returnConnector: ReturnConnector,
  val http: HttpClient,
  val servicesConfig: ServicesConfig
) extends BackendController(cc) with AuthorisedFunctions with DesHelpers {

  val log: Logger = Logger(this.getClass)
  val serviceConfig = new ServicesConfig(runModeConfiguration, runMode)

  implicit val ec: ExecutionContext = cc.executionContext

  private def getSafeId(data: Registration)(implicit hc:HeaderCarrier): Future[Option[SafeId]] = {
    rosmConnector.retrieveROSMDetailsWithoutID(
      RosmRegisterWithoutIDRequest(
      isAnAgent = false,
      isAGroup = false,
      data.companyReg.company,
      data.contact
    )).map(_.fold(Option.empty[SafeId])(x => SafeId(x.safeId).some))
  }


  def submitRegistrationP(
    idType: String,
    idNumber: Option[String],
    data: Registration,
    safeId: SafeId,
    internalId: InternalId,
    providerId: String
  )(implicit hc: HeaderCarrier): Future[Unit] = {

      registrationConnector.send(idType, idNumber, data)(hc, ec) >>= { r =>
        {
          {persistence.pendingCallbacks(r.formBundleNumber) = internalId} >>
          taxEnrolmentConnector.subscribe(
            safeId,
            r.formBundleNumber
          ) >>
          auditing.sendExtendedEvent(
            AuditingHelper.buildRegistrationAudit(
              data, providerId, r.formBundleNumber.some, "SUCCESS"
            )
          ) >> ().pure[Future]
        } recoverWith {
          case e =>
            auditing.sendExtendedEvent(
              AuditingHelper.buildRegistrationAudit(
                data, providerId, None, "ERROR"
              )
            ) map {
              Logger.warn(s"Error with DST Registration ${e.getMessage}")
              throw e
            }
        }
      }
  }

  def submitRegistration(): Action[JsValue] = loggedIn.async(parse.json) { implicit request =>
    withJsonBody[Registration](data => {
      {persistence.registrations(request.internalId) = data} >>
      ((data.companyReg.utr, data.companyReg.safeId, data.companyReg.useSafeId) match {

        case (_, _, true) =>
            for {
              safeId <- getSafeId(data)

              reg <- submitRegistrationP("safe", safeId, data, safeId.get, request.internalId, request.providerId)
            } yield (reg, safeId)
          case (Some(utr), Some(_), false) =>
            for {
              reg <- submitRegistrationP("utr", utr.some, data, data.companyReg.safeId.get, request.internalId, request.providerId)
            } yield (reg, data.companyReg.safeId)
          case _ =>
            for {
              reg <- submitRegistrationP("utr", getUtrFromAuth(request.enrolments), data, data.companyReg.safeId.get, request.internalId, request.providerId)
            } yield (reg, data.companyReg.safeId)
      }) >> emailConnector.sendSubmissionReceivedEmail(
        data.contact,
        data.companyReg.company.name,
        data.ultimateParent
      ) >> Future.successful(Ok(JsNull))
    })
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
              parent   <- OptionT.fromOption[Future](updatedR.ultimateParent)
              _        <- OptionT.liftF(
                            emailConnector
                              .sendConfirmationEmail(
                                updatedR.contact,
                                updatedR.companyReg.company.name,
                                parent.name,
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
            case _ => OptionT.some[Future](r)
          }
        } yield processedRegistration).fold(NotFound(JsNull))(y => Ok(Json.toJson(y)))
      case None => NotFound.pure[Future]
    }
  }

}
