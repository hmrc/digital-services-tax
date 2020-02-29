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

import data.{percentFormat => _, _}, BackendAndFrontendJson._

import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders, AuthorisedFunctions}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.services.JsonSchemaChecker
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import services.FutureVolatilePersistence
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve._, v2.Retrievals._
import scala.concurrent._
import java.time.LocalDate

@Singleton()
class ReturnsController @Inject()(
  val authConnector: AuthConnector,
  val runModeConfiguration: Configuration,
  val runMode: RunMode,
  appConfig: AppConfig,
  cc: ControllerComponents,
  persistence: FutureVolatilePersistence
) extends BackendController(cc) with AuthorisedFunctions {

  val log = Logger(this.getClass())
  log.error(s"startup ${this.getClass} logging")
  val serviceConfig = new ServicesConfig(runModeConfiguration, runMode)

  implicit val ec: ExecutionContext = cc.executionContext

  def submitReturn(year: Int): Action[JsValue] = Action.async(parse.json) { implicit request =>
    authorised(AuthProviders(GovernmentGateway)).retrieve(internalId) { uid =>
      val userId = uid.getOrElse(
        throw new java.security.AccessControlException("No internalId available")
      )
      persistence.registrations(userId).flatMap { reg =>
        val period: Period = reg.period(year).getOrElse(
          throw new IllegalArgumentException(s"No period found for $year")
        )
        withJsonBody[Return](data => {
          (persistence.returns(reg, period) = data) map { _ => Ok("") }
        })
      }
    }
  }

  def lookupOutstandingReturns(): Action[AnyContent] = Action.async { implicit request =>
    authorised(AuthProviders(GovernmentGateway)).retrieve(internalId) { uid =>
      val userId = uid.getOrElse(
        throw new java.security.AccessControlException("No internalId available")
      )
      persistence.registrations(userId).flatMap { reg =>
        persistence.returns.get(reg) map { submittedReturns =>

          val allYears: List[Period] = {reg.dateLiable.getYear to LocalDate.now.getYear}.toList flatMap (
            reg.period(_).toList
          )

          val applicableYears: List[Period] = allYears diff submittedReturns.keys.toSeq
          Ok(JsArray(applicableYears.map(Json.toJson(_))))
        }
      }
    }
  }
}
