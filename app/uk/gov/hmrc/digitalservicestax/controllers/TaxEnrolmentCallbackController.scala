/*
 * Copyright 2021 HM Revenue & Customs
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

import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Format, JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors._
import uk.gov.hmrc.digitalservicestax.data._
import uk.gov.hmrc.digitalservicestax.services.{AuditingHelper, MongoPersistence}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxEnrolmentCallbackController @Inject()(  val authConnector: AuthConnector,
  val runModeConfiguration: Configuration,
  appConfig: AppConfig,
  cc: ControllerComponents,
  registrationConnector: RegistrationConnector,
  rosmConnector: RosmConnector,
  taxEnrolments: TaxEnrolmentConnector,
  emailConnector: EmailConnector,
  returnConnector: ReturnConnector,
  persistence: MongoPersistence,
  auditing: AuditConnector
) extends BackendController(cc) with AuthorisedFunctions {

  val logger: Logger = Logger(this.getClass)
  implicit val ec: ExecutionContext = cc.executionContext

  object CallbackProcessingException extends Exception("Unable to process tax-enrolments callback")

  def callback(formBundleNumberString: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    val formBundleNumber = FormBundleNumber(formBundleNumberString)
    withJsonBody[CallbackNotification] { body =>
      logger.info(s"Tax-enrolment callback triggered, body: $body")
      if (body.state == "SUCCEEDED") {
        (for {
          dstNumber    <- taxEnrolments.getSubscription(formBundleNumber).map{
                            _.getDSTNumber.getOrElse(throw CallbackProcessingException)}
          reg          <- persistence.pendingCallbacks.process(formBundleNumber, dstNumber)
          period       <- returnConnector.getNextPendingPeriod(dstNumber)
          _            <- emailConnector.sendConfirmationEmail(
                            reg.contact,
                            reg.companyReg.company.name,
                            reg.ultimateParent.fold(CompanyName("unknown")){x => x.name},
                            dstNumber,
                            period
                          )
        } yield {
          auditing.sendExtendedEvent(
            AuditingHelper.buildCallbackAudit(
              body,
              request.uri,
              formBundleNumber,
              "SUCCESS",
              dstNumber.some
            )
          )
          logger.info("Tax-enrolments callback, lookup and save of persistence successful")
          NoContent
        }).recoverWith{
          case e: Exception =>
            logger.warn(s"Error inside SUCCEEDED tax-enrolment callback processing: $e")
            Future(NoContent)
          case _ =>
            Future(NoContent)
        }
      } else {
        auditing.sendExtendedEvent(
          AuditingHelper.buildCallbackAudit(
            body,
            request.uri,
            formBundleNumber,
            "ERROR")
        )
        logger.error(s"Got error from tax-enrolments callback for $formBundleNumber: [${body.errorResponse.getOrElse("")}]")
        Future.successful(NoContent)
      }
    }
  }

}

case class CallbackNotification(state: String, errorResponse: Option[String])

object CallbackNotification {
  implicit val format: Format[CallbackNotification] = Json.format[CallbackNotification]
}



