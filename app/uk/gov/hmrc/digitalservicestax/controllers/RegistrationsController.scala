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
package controllers

import cats.implicits._
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.digitalservicestax.actions._
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors._
import uk.gov.hmrc.digitalservicestax.data.BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.data.{percentFormat => _, _}
import uk.gov.hmrc.digitalservicestax.services.{AuditingHelper, MongoPersistence, TaxEnrolmentService}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent._

@Singleton
class RegistrationsController @Inject() (
  val authConnector: AuthConnector,
  cc: ControllerComponents,
  registrationConnector: RegistrationConnector,
  rosmConnector: RosmConnector,
  taxEnrolmentConnector: TaxEnrolmentConnector,
  emailConnector: EmailConnector,
  persistence: MongoPersistence,
  taxEnrolmentService: TaxEnrolmentService,
  appConfig: AppConfig,
  registrationOrPending: RegisteredOrPending,
  val auditing: AuditConnector,
  loggedIn: LoggedInAction
) extends BackendController(cc)
    with AuthorisedFunctions
    with AuditWrapper {

  val logger: Logger = Logger(this.getClass)

  implicit val ec: ExecutionContext = cc.executionContext
  final val dstServiceName: String  = "HMRC-DST-ORG"

  def submitRegistration(): Action[JsValue] = loggedIn.async(parse.json) { implicit request =>
    withJsonBody[Registration](data =>
      for {
        _      <- persistence.registrations.update(request.internalId, data)
        safeId <- data.companyReg.safeId.fold(rosmConnector.getSafeId(data).map(_.get))(_.pure[Future])
        reg    <- (
                    // these steps are combined for auditing purposes
                    for {
                      r <- registrationConnector.send(
                             idType = if (data.companyReg.useSafeId) "safe" else "utr",
                             idNumber = if (data.companyReg.useSafeId) { safeId }
                             else {
                               data.companyReg.utr.getOrElse(
                                 getUtrFromAuth(
                                   request.enrolments
                                 ).getOrElse(throw new RuntimeException("Rosm not retrieved safeId and not UTR"))
                               )
                             },
                             data
                           )
                      _ <- persistence.pendingCallbacks(r.formBundleNumber) = request.internalId
                      _ <- taxEnrolmentConnector.subscribe(safeId, r.formBundleNumber)
                    } yield r
                  ).auditError(_ => AuditingHelper.buildRegistrationAudit(data, request.providerId, None, "ERROR"))
                    .auditSuccess(r =>
                      AuditingHelper.buildRegistrationAudit(data, request.providerId, Some(r.formBundleNumber), "SUCCESS")
                    )
        _      <- emailConnector.sendSubmissionReceivedEmail(
                    data.contact,
                    data.companyReg.company.name,
                    data.ultimateParent
                  )
      } yield Ok(Json.toJson(reg))
    )
  }

  def getTaxEnrolmentsPendingRegDetails(): Action[AnyContent] = loggedIn.async {
    implicit request: LoggedInRequest[AnyContent] =>
      if (appConfig.dstNewSolutionFeatureFlag) {
        taxEnrolmentService.getPendingDSTRegistration(request.groupId)
      } else Future.successful(NotFound)
  }

  def lookupRegistration(): Action[AnyContent] = loggedIn.async { implicit request: LoggedInRequest[AnyContent] =>
    for {
      regDetails        <- getDstEnrolmentsFromAuth
      pendingRegDetails <- persistence.pendingCallbacks.reverseLookup(request.internalId)
    } yield (regDetails, pendingRegDetails) match {
      case (Some(regDetails), _) if regDetails.registrationNumber.isDefined =>
        Ok(Json.toJson(regDetails))
      case (Some(regDetails), Some(_))                                      =>
        if (regDetails.registrationNumber.isEmpty) {
          logger.info(s"no registration number found in registration details")
        }
        logger.info("pending registration")
        Ok(Json.toJson(regDetails))
      case _                                                                =>
        logger.info("no pending registration")
        NotFound
    }
  }

  def getDstEnrolmentsFromAuth(implicit request: LoggedInRequest[AnyContent]): Future[Option[Registration]] =
    registrationOrPending.getRegistration(request, appConfig)
}
