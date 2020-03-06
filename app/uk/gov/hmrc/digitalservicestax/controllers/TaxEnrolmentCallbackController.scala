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

import cats.data.OptionT
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Format, JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors._
import uk.gov.hmrc.digitalservicestax.data.{DSTRegNumber, FormBundleNumber, Registration}
import uk.gov.hmrc.digitalservicestax.services.MongoPersistence
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import cats.implicits._

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
  persistence: MongoPersistence
) extends BackendController(cc) with AuthorisedFunctions {

  val serviceConfig = new ServicesConfig(runModeConfiguration, runMode)

  implicit val ec: ExecutionContext = cc.executionContext

  object CallbackProcessingException extends Exception("Unable to process tax-enrolments callback")

  def callback(formBundleNumberString: String): Action[JsValue] = Action.async(parse.json) { implicit request =>

    val formBundleNumber = FormBundleNumber(formBundleNumberString)
    withJsonBody[CallbackNotification] { body =>
      if (body.state == "SUCCEEDED") {
        for {
          teSub <- taxEnrolments.getSubscription(formBundleNumber)
          dstRef = getDSTNumber(teSub).getOrElse(throw CallbackProcessingException)
          _  <- persistence.pendingCallbacks.process(formBundleNumber, dstRef)
          // TODO @Adam.Dye here for reg number email

//          _ <- sendNotificationEmail(pendingSub.map(_.subscription.orgName), pendingSub.map(_.subscription.contact.email), getSdilNumber(teSub), formBundleNumber)
//          _ <- auditing.sendExtendedEvent(buildAuditEvent(body, request.uri, formBundleNumber))
        } yield {
          Logger.info("Tax-enrolments callback, lookup and save of persistence successful")
          NoContent
        }
      } else {
        Logger.error(s"Got error from tax-enrolments callback for $formBundleNumber: [${body.errorResponse.getOrElse("")}]")
        // TODO
//        auditing.sendExtendedEvent(
//          buildAuditEvent(body, request.uri, formBundleNumber)) map {
//          _ => NoContent
//        }
        Future.successful(NoContent)
      }
    }
  }


  private def getDSTNumber(taxEnrolmentsSubscription: TaxEnrolmentsSubscription): Option[DSTRegNumber] = {
    taxEnrolmentsSubscription.identifiers.getOrElse(Nil).collectFirst {
      case Identifier(_, value) if value.slice(2, 5) == "DST" => DSTRegNumber(value)
    }
  }


  //  private def sendNotificationEmail(orgName: Option[String], email: Option[String], sdilNumber: Option[String], formBundleNumber: String)
//    (implicit hc: HeaderCarrier): Future[Unit] = {
//    (orgName, email) match {
//      case (Some(o), Some(e)) => sdilNumber match {
//        case Some(s) => emailConnector.sendConfirmationEmail(o, e, s)
//        case None => Future.successful(Logger.error(s"Unable to send email for form bundle $formBundleNumber as enrolment is missing SDIL Number"))
//      }
//      case _ => Future.successful(Logger.error(s"Received callback for form bundle number $formBundleNumber, but no pending record exists"))
//    }
//  }
//
//
//  private def buildAuditEvent(callback: CallbackNotification, path: String, subscriptionId: String)(implicit hc: HeaderCarrier) = {
//    implicit val callbackFormat: OWrites[CallbackNotification] = Json.writes[CallbackNotification]
//    val detailJson = Json.obj(
//      "subscriptionId" -> subscriptionId,
//      "url" -> s"${serviceConfig.baseUrl("tax-enrolments")}/tax-enrolments/subscriptions/$subscriptionId",
//      "outcome" -> (callback.state match {
//        case "SUCCEEDED" => "SUCCESS"
//        case _ => "ERROR"
//      }),
//      "errorResponse" -> callback.errorResponse
//    )
//    new TaxEnrolmentEvent(callback.state, path, detailJson)
//  }

}

case class CallbackNotification(state: String, errorResponse: Option[String])

object CallbackNotification {
  implicit val format: Format[CallbackNotification] = Json.format[CallbackNotification]
}



