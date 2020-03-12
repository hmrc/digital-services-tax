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

package uk.gov.hmrc.digitalservicestax.controllers

import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Format, JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.internalId
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors._
import uk.gov.hmrc.digitalservicestax.data._
import uk.gov.hmrc.digitalservicestax.services.{AuditingHelper, MongoPersistence}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxEnrolmentCallbackController @Inject()(  val authConnector: AuthConnector,
  val runModeConfiguration: Configuration,
  val runMode: RunMode,
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

  val serviceConfig = new ServicesConfig(runModeConfiguration, runMode)

  implicit val ec: ExecutionContext = cc.executionContext

  object CallbackProcessingException extends Exception("Unable to process tax-enrolments callback")

  def callback(formBundleNumberString: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    val formBundleNumber = FormBundleNumber(formBundleNumberString)
    withJsonBody[CallbackNotification] { body =>
      if (body.state == "SUCCEEDED") {
        for {
          dstNumber    <- taxEnrolments.getSubscription(formBundleNumber).map{
                            getDSTNumber(_).getOrElse(throw CallbackProcessingException)}
          reg          <- persistence.pendingCallbacks.process(formBundleNumber, dstNumber)
          period       <- returnConnector.getNextPendingPeriod(dstNumber)
          _            <- emailConnector.sendConfirmationEmail(
                            reg.contact,
                            reg.ultimateParent.fold(NonEmptyString("unknown")){x => x.name},
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
          Logger.info("Tax-enrolments callback, lookup and save of persistence successful")
          NoContent
        }
      } else {
        auditing.sendExtendedEvent(
          AuditingHelper.buildCallbackAudit(
            body,
            request.uri,
            formBundleNumber,
            "Error")
        )
        Logger.error(s"Got error from tax-enrolments callback for $formBundleNumber: [${body.errorResponse.getOrElse("")}]")
        Future.successful(NoContent)
      }
    }
  }

  private def getDSTNumber(taxEnrolmentsSubscription: TaxEnrolmentsSubscription): Option[DSTRegNumber] = {
    taxEnrolmentsSubscription.identifiers.getOrElse(Nil).collectFirst {
      case Identifier(_, value) if value.slice(2, 5) == "DST" => DSTRegNumber(value)
    }
  }
}

case class CallbackNotification(state: String, errorResponse: Option[String])

object CallbackNotification {
  implicit val format: Format[CallbackNotification] = Json.format[CallbackNotification]
}



