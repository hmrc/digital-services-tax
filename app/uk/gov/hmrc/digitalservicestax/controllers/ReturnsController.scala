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
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.digitalservicestax.actions._
import uk.gov.hmrc.digitalservicestax.data.BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.data.{Period, percentFormat => _, _}
import uk.gov.hmrc.digitalservicestax.services.{AuditingHelper, MongoPersistence}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent._

@Singleton()
class ReturnsController @Inject() (
  val authConnector: AuthConnector,
  val runModeConfiguration: Configuration,
  cc: ControllerComponents,
  persistence: MongoPersistence,
  connector: connectors.ReturnConnector,
  auditing: AuditConnector,
  registered: Registered,
  loggedIn: LoggedInAction
) extends BackendController(cc)
    with AuthorisedFunctions
    with Logging {

  implicit val ec: ExecutionContext = cc.executionContext

  def submitReturn(periodKeyString: String): Action[JsValue] =
    loggedIn.andThen(registered).async(parse.json) { implicit request =>
      val regNo     = request.registration.registrationNumber.get
      val periodKey = Period.Key(periodKeyString)
      withJsonBody[Return] { data =>
        for {
          allPeriods        <- connector.getPeriods(regNo)
          (period, previous) = allPeriods
                                 .find {
                                   _._1.key == periodKey
                                 }
                                 .getOrElse {
                                   throw new NoSuchElementException(s"no period found for $periodKey")
                                 }
          _                 <- connector.send(regNo, period, data, previous.isDefined)
          _                 <- auditing.sendExtendedEvent(
                                 AuditingHelper
                                   .buildReturnSubmissionAudit(regNo, request.authRequest.providerId, period, data, previous.isDefined)
                               )
          _                 <- persistence.returns(request.registration, period.key) = data
          _                 <- auditing.sendExtendedEvent(AuditingHelper.buildReturnResponseAudit("SUCCESS"))
        } yield Ok(JsNull)
      }.recoverWith {
        case e: NoSuchElementException =>
          Future.successful(NotFound)
        case e                         =>
          auditing.sendExtendedEvent(
            AuditingHelper.buildReturnResponseAudit("ERROR", e.getMessage.some)
          ) map {
            logger.warn(s"Error with DST Return ${e.getMessage}")
            throw e
          }
      }
    }

  def lookupOutstandingReturns(): Action[AnyContent] =
    loggedIn.andThen(registered).async { implicit request =>
      val regNo = request.registration.registrationNumber.get
      connector.getPeriods(regNo).map { a =>
        Ok(
          JsArray(
            a.collect { case (p, None) => Json.toJson(p) }
          )
        )
      }
    }

  def lookupAllReturns(): Action[AnyContent] =
    loggedIn.andThen(registered).async { implicit request =>
      val regNo = request.registration.registrationNumber.get
      connector.getPeriods(regNo).map { a =>
        Ok(
          JsArray(
            a.collect { case (p, _) => Json.toJson(p) }
          )
        )
      }
    }

  def lookupAmendableReturns(): Action[AnyContent] =
    loggedIn.andThen(registered).async { implicit request =>
      val regNo = request.registration.registrationNumber.get

      connector.getPeriods(regNo).map { a =>
        Ok(
          JsArray(
            a.collect {
              case (p, Some(_)) if p.returnDue.isAfter(LocalDate.now.minusYears(1)) =>
                Json.toJson(p)
            }
          )
        )
      }
    }

  def getReturn(periodKeyString: String): Action[AnyContent] =
    loggedIn.andThen(registered).async { implicit request =>
      val periodKey = Period.Key(periodKeyString)

       persistence.returns(request.registration, periodKey).map { returnRecord =>
         Ok(Json.toJson(returnRecord))
       } recoverWith {
         case _: Throwable =>
           Future.successful(NotFound)
       }
    }

}
