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

import data._, BackendAndFrontendJson._
import connectors.FinancialDataConnector
import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Logger}
import scala.concurrent._
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders, AuthorisedFunctions}
import uk.gov.hmrc.digitalservicestax.actions._
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.api.libs.json._
import actions._

@Singleton
class FinancialTransactionsController @Inject()(
  val authConnector: AuthConnector,    
  cc: ControllerComponents,
  val runModeConfiguration: Configuration,
  val runMode: RunMode,
  loggedIn: LoggedInAction,
  registered: Registered,
  connector: FinancialDataConnector
) extends BackendController(cc) with AuthorisedFunctions {

  val serviceConfig = new ServicesConfig(runModeConfiguration, runMode)
  implicit val ec: ExecutionContext = cc.executionContext

  def lookup(): Action[JsValue] =
    loggedIn.andThen(registered).async(parse.json) { implicit request =>
      val regNo = request.registration.registrationNumber.get
      connector.retrieveFinancialData(regNo).map { items =>
        val json = JsArray(items.map{Json.toJson(_)})
        Ok(json)
      }
    }

}
