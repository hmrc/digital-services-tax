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

package uk.gov.hmrc.digitalservicestax.controllers

import play.api.Logger
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.digitalservicestax.connectors._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxEnrolmentController @Inject()(
                                        val authConnector: AuthConnector,
                                        cc: ControllerComponents,
                                        taxEnrolments: TaxEnrolmentConnector,
                                      ) extends BackendController(cc)
  with AuthorisedFunctions {

  val logger: Logger = Logger(this.getClass)
  implicit val ec: ExecutionContext = cc.executionContext

  def getSubscription(groupId: String): Action[AnyContent] =Action.async { implicit request =>
    authorised() {
      taxEnrolments.getSubscriptionByGroupId(groupId).map { taxEnrolmentSubscription =>
        if(taxEnrolmentSubscription.errorResponse.isDefined) {
          logger.error(
            s"Error response received while getting Tax enrolment subscription by groupId: " +
              s"${taxEnrolmentSubscription.errorResponse.get}")
          InternalServerError
        } else {
          taxEnrolmentSubscription.getDSTNumberWithSucceededState match {
            case Some(dstNumber) => Ok(dstNumber)
            case _ => NotFound
          }
        }
      }.recoverWith{
        case ex: Exception => {
          logger.error(
            s"Unexpected error response received while getting Tax enrolment subscription by groupId: " +
              s"${ex.getMessage}")
          Future(InternalServerError)
        }
      }
    }
  }
}
