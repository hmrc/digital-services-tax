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

package uk.gov.hmrc.digitalservicestax
package controllers

import cats.implicits._
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
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent._

@Singleton
class RegistrationsController @Inject()(
  val authConnector: AuthConnector,
  val runModeConfiguration: Configuration,
  val appConfig: AppConfig,
  cc: ControllerComponents,
  registrationConnector: RegistrationConnector,
  rosmConnector: RosmConnector,
  taxEnrolmentConnector: TaxEnrolmentConnector,
  emailConnector: EmailConnector,
  persistence: MongoPersistence,
  val auditing: AuditConnector,
  loggedIn: LoggedInAction,
  val http: HttpClient
) extends BackendController(cc) with AuthorisedFunctions with DesHelpers with AuditWrapper{

  val logger: Logger = Logger(this.getClass)

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
            data
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

  def lookupRegistration(): Action[AnyContent] = loggedIn.async { implicit request: LoggedInRequest[AnyContent] =>
    for {
      a <- persistence.registrations.get(request.internalId)
      b <- persistence.pendingCallbacks.reverseLookup(request.internalId)
    } yield (a, b) match {
      case (Some(r), _) if r.registrationNumber.isDefined =>
        Ok(Json.toJson(r))
      case (Some(r), _) if r.registrationNumber.isEmpty =>
        logger.info("No Registration Number found for user")
        NotFound
      case (Some(r), p) if p.nonEmpty =>
        logger.info(s"pending registration")
        Ok(Json.toJson(r))
      case _ =>
        logger.info("no pending registration")
        NotFound
    }
  }

}
