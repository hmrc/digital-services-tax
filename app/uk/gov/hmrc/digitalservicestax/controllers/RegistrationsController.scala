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
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders, AuthorisedFunctions}
import uk.gov.hmrc.digitalservicestax.data._, BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.actions._
import uk.gov.hmrc.digitalservicestax.backend_data.RosmRegisterWithoutIDRequest
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors._
import uk.gov.hmrc.digitalservicestax.backend_data.RosmRegisterWithoutIDRequest
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors.{RegistrationConnector, RosmConnector, TaxEnrolmentConnector}
import uk.gov.hmrc.digitalservicestax.data.{percentFormat => _, _}
import uk.gov.hmrc.digitalservicestax.services.{AuditingHelper, MongoPersistence}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.controller.BackendController

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
  registered: RegisteredOrPending
) extends BackendController(cc) with AuthorisedFunctions {

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

  def submitRegistration(): Action[JsValue] = loggedIn.async(parse.json) { implicit request =>

      withJsonBody[Registration](data => {
        ((data.companyReg.utr, data.companyReg.safeId, data.companyReg.useSafeId) match {
          case (_, _, true) =>
            for {
              safeId <- getSafeId(data)
              reg <- registrationConnector.send("safe", safeId, data)
            } yield (reg, safeId)
          case (Some(utr), Some(safeId),false) =>
            for {
              reg <- registrationConnector.send("utr", utr.some, data)
            } yield (reg, data.companyReg.safeId)
          case _ =>
            for {
              reg <- registrationConnector.send("utr", getUtrFromAuth(request.enrolments), data)
            } yield (reg, data.companyReg.safeId)
        }).flatMap {
          case (Some(r), Some(safeId: SafeId)) => {
            {persistence.registrations(request.internalId) = data} >>             
            {persistence.pendingCallbacks(r.formBundleNumber) = request.internalId} >> 
            taxEnrolmentConnector.subscribe(
              safeId,
              r.formBundleNumber
            ) >>
              emailConnector.sendSubmissionReceivedEmail(
                data.contact,
                data.ultimateParent
              ) >>
              auditing.sendExtendedEvent(
                AuditingHelper.buildRegistrationAudit(
                  data, request.providerId, r.formBundleNumber.some, "SUCCESS"
                )
              ) >>
              Future.successful(Ok(Json.toJson(r)))
          } recoverWith {
            case e =>
              auditing.sendExtendedEvent(
                AuditingHelper.buildRegistrationAudit(
                  data, request.providerId, None, "ERROR"
                )
              ) map {
                Logger.warn(s"Error with DST Registration ${e.getMessage}")
                throw e
              }
          }
        }
      })
    
  }

  def lookupRegistration(): Action[AnyContent] = loggedIn.andThen(registered) { request => 
    Ok(Json.toJson(request.registration))
  }
}
