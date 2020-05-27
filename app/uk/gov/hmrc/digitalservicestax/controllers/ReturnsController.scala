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
import actions._
import services.{AuditingHelper, MongoPersistence}

import cats.implicits._
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import scala.concurrent._
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.controller.BackendController

class ReturnsController(
  val authConnector: AuthConnector,
  cc: ControllerComponents,
  persistence: MongoPersistence,
  connector: connectors.ReturnConnector,
  auditing: AuditConnector,
  registered: Registered,
  loggedIn: LoggedInAction
) extends BackendController(cc) with AuthorisedFunctions {

  val log = Logger(this.getClass())
  implicit val ec: ExecutionContext = cc.executionContext

  def submitReturn(periodKeyString: String): Action[JsValue] =
    loggedIn.andThen(registered).async(parse.json) { implicit request =>
      val regNo = request.registration.registrationNumber.get
      val periodKey = Period.Key(periodKeyString)
      withJsonBody[Return](data => {
        for {
          allPeriods <- connector.getPeriods(regNo)
          (period, previous) = allPeriods.find{_._1.key == periodKey}.getOrElse {
            throw new NoSuchElementException(s"no period found for $periodKey")
          }
          _ <- connector.send(regNo, period, data, previous.isDefined)
          _ <- auditing.sendExtendedEvent(AuditingHelper.buildReturnSubmissionAudit(regNo, request.authRequest.providerId, period, data, previous.isDefined))
          _ <- persistence.returns(request.registration, period.key) = data
          _ <- auditing.sendExtendedEvent(AuditingHelper.buildReturnResponseAudit("SUCCESS"))
        } yield {
          Ok(JsNull)
        }
      }).recoverWith {
        case e: NoSuchElementException =>
          Future.successful(NotFound)
        case e =>
          auditing.sendExtendedEvent(
            AuditingHelper.buildReturnResponseAudit("ERROR", e.getMessage.some)
          ) map {
            Logger.warn(s"Error with DST Return ${e.getMessage}")
            throw e
          }
      }
  }

  def lookupOutstandingReturns(): Action[AnyContent] =
    loggedIn.andThen(registered).async { implicit request =>
      val regNo = request.registration.registrationNumber.get
      connector.getPeriods(regNo).map { a => Ok(JsArray(
          a.collect { case (p, None) => Json.toJson(p) }
        )) }
    }
}
