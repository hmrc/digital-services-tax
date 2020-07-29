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


import java.time.LocalDate

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatestplus.mockito._
import org.scalatestplus.play._
import play.api.Configuration
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.retrieve.{Credentials, EmptyRetrieval}
import uk.gov.hmrc.digitalservicestax.actions.{LoggedInAction, LoggedInRequest, Registered, RegisteredRequest}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors
import uk.gov.hmrc.digitalservicestax.data.Period.Key
import uk.gov.hmrc.digitalservicestax.data.{CompanyRegWrapper, ContactDetails, DSTRegNumber, Period, Registration}
import uk.gov.hmrc.digitalservicestax.services.MongoPersistence
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.RunMode

import scala.concurrent.{ExecutionContext, Future}


class ReturnsControllerSpec extends PlaySpec with MockitoSugar with Results {
  private val mockAuthConnector = mock[AuthConnector]
  private val mockRunModeConfiguration = mock[Configuration]
  private val mockRunMode = mock[RunMode]
  private val mockAppConfig = mock[AppConfig]
  private val mockCc = mock[ControllerComponents]
  private val mockPersistence = mock[MongoPersistence]
  private val mockConnector = mock[connectors.ReturnConnector]
  private val mockAuditing = mock[AuditConnector]
  private val mockCompanyReg = mock[CompanyRegWrapper]
  private val mockContact = mock[ContactDetails]
  private val mockMcc = mock[MessagesControllerComponents]


  val regObj: Registration = Registration(mockCompanyReg, None, None, mockContact, LocalDate.now().minusWeeks(1),LocalDate.now().plusMonths(3), Some(DSTRegNumber("ASDST1010101010")))

  def loginReq[A]: LoggedInRequest[A] = new LoggedInRequest[A](null, null, null, FakeRequest().withBody(AnyContent().asInstanceOf[A]))

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

  object TestReturnsController extends ReturnsController(
    authConnector = mockAuthConnector,
    runModeConfiguration = mockRunModeConfiguration,
    runMode = mockRunMode,
    appConfig = mockAppConfig,
    cc = stubControllerComponents(),
    persistence = mockPersistence,
    connector = mockConnector,
    auditing = mockAuditing,
    registered = mockRegistered,
    loggedIn = loginReturn
  )


  "Amendable Returns" must {
    "return none" in {
      val periods: Future[List[(Period, Option[LocalDate])]] =
        Future.successful(List(Period(
          LocalDate.now().minusYears(2).minusWeeks(1),
          LocalDate.now().minusYears(1).minusWeeks(1),
          LocalDate.now().minusWeeks(1),
          Period.Key("001")) ->
          Some(LocalDate.now().minusWeeks(1))))

      when(mockConnector.getPeriods(any())(any(), any())) thenReturn periods

      val result: Future[Result] = TestReturnsController.lookupAmendableReturns().apply(FakeRequest())

      val expected = status(result)
      expected mustBe 200
    }

  }


}
