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

package uk.gov.hmrc.digitalservicestax.test

import cats.implicits._
import play.api.mvc.{Action, AnyContent, ControllerComponents, MessagesControllerComponents}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestController @Inject() (
  connector: TestConnector,
  cc: ControllerComponents,
  mcc: MessagesControllerComponents,
  val appConfig: AppConfig
)(implicit
  ec: ExecutionContext
) extends BackendController(cc)
    with ExtraActions {

  def triggerTaxEnrolmentCallback(seed: String): Action[AnyContent] = Action.async { implicit request =>
    connector.trigger("trigger/callback/te", seed).map { response =>
      val dstRefNumberFromHeader = response
        .header("dstRegistrationNumber")
        .getOrElse(throw new Exception("DST Registration number missing in headers"))
      Ok(s"tax enrolment callback triggered with dstReference:$dstRefNumberFromHeader")
    }
  }

  override def messagesControllerComponents: MessagesControllerComponents = mcc
}
