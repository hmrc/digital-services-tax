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
import data.{percentFormat => _, _}, BackendAndFrontendJson._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders, AuthorisedFunctions, Enrolments}
import uk.gov.hmrc.digitalservicestax.backend_data.{RosmRegisterWithoutIDRequest, RosmWithoutIDResponse}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors.{RegistrationConnector, RosmConnector}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent._


@Singleton()
class RegistrationsController @Inject()(
  val authConnector: AuthConnector,
  val runModeConfiguration: Configuration,
  val runMode: RunMode,
  appConfig: AppConfig,
  cc: ControllerComponents,
  registrationConnector: RegistrationConnector,
  rosmConnector: RosmConnector
) extends BackendController(cc) with AuthorisedFunctions {

  val log = Logger(this.getClass())
  log.error(s"startup ${this.getClass} logging")
  val serviceConfig = new ServicesConfig(runModeConfiguration, runMode)

  implicit val ec: ExecutionContext = cc.executionContext

  private def getSafeId(data: Registration)(implicit hc:HeaderCarrier): Future[Option[SafeId]] = {
    rosmConnector.retrieveROSMDetailsWithoutID(
      RosmRegisterWithoutIDRequest(
        isAnAgent = false,
        isAGroup = false,
        data.company,
        data.contact
      )).map(_.fold(Option.empty[SafeId])(x => SafeId(x.safeId).some))
  }


  import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.allEnrolments

  private def getUtr(enrolments: Enrolments): Option[UTR] = {
    enrolments
      .getEnrolment("IR-CT")
      .orElse(enrolments.getEnrolment("IR-SA"))
      .flatMap(_.getIdentifier("UTR").map(x => UTR(x.value)))
  }

  def submitRegistration(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    authorised(AuthProviders(GovernmentGateway)).retrieve(allEnrolments) { enrolments =>
      withJsonBody[Registration](data => {
        ((data.utr, getUtr(enrolments), data.useSafeId) match {
          case (_, _, true) =>
            for {
              safeId <- getSafeId(data)
              reg <- registrationConnector.send("safeid", safeId, data)
            } yield reg
          case (Some(utr), _, false) =>
            for {
              reg <- registrationConnector.send("utr", utr.some, data)
            } yield reg
          case (_, Some(utrFromAuth), _) =>
            for {
              reg <- registrationConnector.send("utr", utrFromAuth.some, data)
            } yield reg
          case _ =>
            throw new IllegalArgumentException(s"Missing identifier, neither utr nor safeid supplied")
        }).map {
          case Some(r) =>
            Ok(Json.toJson(r))
          case _ => NotFound
        }
      })
    }
  }

  def lookupRegistration(): Action[AnyContent] = Action.async { implicit request =>

    val response: Option[Registration] = ??? // TODO

    Future.successful(
      response match {
        case Some(r) =>
          Ok(Json.toJson(r))
        case None => NotFound
      }
    )
  }
}
