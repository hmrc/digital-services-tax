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
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders, AuthorisedFunctions}
import uk.gov.hmrc.digitalservicestax.data._, BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.actions._
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors._
import uk.gov.hmrc.digitalservicestax.backend_data.{RosmRegisterWithoutIDRequest, RegistrationResponse}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors.{RegistrationConnector, RosmConnector, TaxEnrolmentConnector}
import uk.gov.hmrc.digitalservicestax.data.{percentFormat => _, _}
import uk.gov.hmrc.digitalservicestax.services.{AuditingHelper, MongoPersistence}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import ltbs.resilientcalls._
import java.time.LocalDateTime

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
  resilienceProvider: DstMongoProvider,
  val http: HttpClient,
  val servicesConfig: ServicesConfig
) extends BackendController(cc) with AuthorisedFunctions with DesHelpers {

  val log = Logger(this.getClass())
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
  ): Future[Unit] = {
    implicit val hc = addHeaders(new HeaderCarrier)

      registrationConnector.send(idType, idNumber, data)(hc, ec) >>= { r =>
        {
          {persistence.pendingCallbacks(r.formBundleNumber) = internalId} >>
          // we cannot subscribe the user to tax enrolments as the HC
          // will not have the users bearer-token, make a note to subscribe
          // them instead as soon as they log in
          {persistence.pendingEnrolments(internalId) = (safeId, r.formBundleNumber)} >>
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

  val resilientSendP = {
    import BackendAndFrontendJson._

    resilienceProvider.apply(
      "send-registration",
      {submitRegistrationP _}.tupled,
      DesRetryRule
    )
  }

  def submitRegistration(): Action[JsValue] = loggedIn.async(parse.json) { implicit request =>
    withJsonBody[Registration](data => {
      {persistence.registrations(request.internalId) = data} >>
      ((data.companyReg.utr, data.companyReg.safeId, data.companyReg.useSafeId) match {

        case (_, _, true) =>
            for {
              safeId <- getSafeId(data)
              
              reg <- resilientSendP.async(("safe", safeId, data, safeId.get, request.internalId, request.providerId))
            } yield (reg, safeId)
          case (Some(utr), Some(safeId),false) =>
            for {
              reg <- resilientSendP.async(("utr", utr.some, data, data.companyReg.safeId.get, request.internalId, request.providerId))
            } yield (reg, data.companyReg.safeId)
          case _ =>
            for {
              reg <- resilientSendP.async(("utr", getUtrFromAuth(request.enrolments), data, data.companyReg.safeId.get, request.internalId, request.providerId))
            } yield (reg, data.companyReg.safeId)
      }) >> emailConnector.sendSubmissionReceivedEmail(
        data.contact,
        data.ultimateParent
      ) >> Future.successful(Ok(JsNull))
    })
  }

  def lookupRegistration(): Action[AnyContent] = loggedIn.async { implicit request =>
    persistence.pendingEnrolments.consume(request.internalId).flatMap {
      case Some((safeId, formBundleNumber)) =>
        // the user has registered with ETMP, but is not yet subscribed
        // with tax enrolments
        taxEnrolmentConnector.subscribe(
          safeId,
          formBundleNumber
        )
      case None => ().pure[Future]
    } >>
    persistence.registrations.get(request.internalId).map { response =>
      response match {
        case Some(r) =>
          Ok(Json.toJson(r))
        case None => NotFound
      }
    }
  }

  def tick() = Action.async {
    resilienceProvider.tick() >> Future(Ok("done"))
  }

}
