/*
 * Copyright 2022 HM Revenue & Customs
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

package it.uk.gov.hmrc.digitalservicestax.helpers

import org.scalatestplus.mockito._
import org.scalatestplus.play._
import play.api.Configuration
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolments}
import uk.gov.hmrc.digitalservicestax.actions.{LoggedInAction, LoggedInRequest, Registered, RegisteredRequest}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors
import uk.gov.hmrc.digitalservicestax.connectors.ReturnConnector
import uk.gov.hmrc.digitalservicestax.data.{CompanyRegWrapper, ContactDetails, DSTRegNumber, InternalId, Registration}
import uk.gov.hmrc.digitalservicestax.services.MongoPersistence
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

trait ControllerBaseSpec  extends PlaySpec with MockitoSugar with Results {

   val mockAuthConnector: AuthConnector = mock[AuthConnector]
   val mockRunModeConfiguration: Configuration = mock[Configuration]
   val mockAppConfig: AppConfig = mock[AppConfig]
   val mockPersistence: MongoPersistence = mock[MongoPersistence]
   val mockConnector: ReturnConnector = mock[connectors.ReturnConnector]
   val mockAuditing: AuditConnector = mock[AuditConnector]
   val mockCompanyReg: CompanyRegWrapper = mock[CompanyRegWrapper]
   val mockContact: ContactDetails = mock[ContactDetails]
   val mockMcc: MessagesControllerComponents = mock[MessagesControllerComponents]
   val mockEnrolments: Enrolments = mock[Enrolments]

  val regObj: Registration = Registration(mockCompanyReg, None, None, mockContact, LocalDate.now().minusWeeks(1), LocalDate.now().plusMonths(3), Some(DSTRegNumber("ASDST1010101010")))

  def loginReq[A]: LoggedInRequest[A] = new LoggedInRequest[A](InternalId("Int-aaff66"), mockEnrolments, "", FakeRequest().withBody(AnyContent().asInstanceOf[A]))

  val loginReturn: LoggedInAction = new LoggedInAction(mockMcc, mockAuthConnector) {
    override def refine[A](request: Request[A]): Future[Either[Result, LoggedInRequest[A]]] =
      Future.successful(Right(loginReq[A]))

    override def parser: BodyParser[AnyContent] = stubBodyParser()
  }

  val mockRegistered: Registered = new Registered(mockPersistence) {
    override def refine[A](request: LoggedInRequest[A]): Future[Either[Result, RegisteredRequest[A]]] =
      Future.successful(Right(RegisteredRequest[A](regObj, loginReq[A])))
  }

  implicit lazy val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global


}