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

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders, AuthorisedFunctions}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors.RosmConnector
import uk.gov.hmrc.digitalservicestax.services.JsonSchemaChecker
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.ExecutionContext

@Singleton()
class RosmController @Inject()(
  val authConnector: AuthConnector,
  rosmConnector: RosmConnector,
  val runModeConfiguration: Configuration,
  val runMode: RunMode,
  appConfig: AppConfig,
  cc: ControllerComponents
) extends BackendController(cc) with AuthorisedFunctions {
  val log = Logger(this.getClass())
  log.error(s"startup ${this.getClass} logging")
  val serviceConfig = new ServicesConfig(runModeConfiguration, runMode)

  implicit val ec: ExecutionContext = cc.executionContext

//  def lookupWithoutId: Action[JsValue] = Action.async(parse.json) { implicit request =>
//  import uk.gov.hmrc.digitalservicestax.backend_data.RosmRegisterWithoutIDRequest
  //    import backend_data.RosmFormats._
////    authorised(AuthProviders(GovernmentGateway)) {
//      withJsonBody[RosmRegisterWithoutIDRequest](data => {
//        rosmConnector.retrieveROSMDetailsWithoutID(data).map {
//          case Some(r) =>
//            Ok(Json.toJson(r))
//          case _ => NotFound
//        }
//      })
////    }
//  }

  def lookupCompany(): Action[AnyContent] = Action.async { implicit request =>

//    authorised(AuthProviders(GovernmentGateway)) {

      val utr: String = ??? // TODO
      rosmConnector.retrieveROSMDetails(
        utr
      ).map {
        case Some(r) =>
          import data.BackendAndFrontendJson._
          JsonSchemaChecker[data.Company](r, "rosm-response")
          Ok(Json.toJson(r))
        case _ =>
          log.warn(s"No record found for UTR $utr")
          NotFound
      }

//    }
  }

  def lookupWithIdCheckPostcode(utr: String, postcode: String): Action[AnyContent] = Action.async { implicit request =>
//    authorised(AuthProviders(GovernmentGateway)) {
      rosmConnector.retrieveROSMDetails(
        utr
      ).map {
        case Some(r) if r.address.postalCode == postcode =>

          import data.BackendAndFrontendJson._
          JsonSchemaChecker[data.Company](r, "rosm-response")
          Ok(Json.toJson(r))
        case Some(r) =>
          log.warn(s"Record found for UTR $utr, but postcode is '${r.address.postalCode}' " +
            s", not user supplied '${postcode}'.")
          NotFound
        case _ =>
          log.warn(s"No record found for UTR $utr")
          NotFound
      }

//    }
  }
}