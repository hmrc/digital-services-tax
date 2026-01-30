/*
 * Copyright 2026 HM Revenue & Customs
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

package it.uk.gov.hmrc.digitalservicestax.controllers.actions

import com.google.inject.Inject
import it.uk.gov.hmrc.digitalservicestax.controllers.actions.FakeIdentifierRegisteredAction.givenRegistration
import it.uk.gov.hmrc.digitalservicestax.controllers.actions.FakeIdentifierRegistrationAction.internalId
import play.api.mvc._
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.digitalservicestax.actions.{IdentifierAction, LoggedInRequest, RegisteredActionRefiner, RegisteredRequest}
import uk.gov.hmrc.digitalservicestax.data
import uk.gov.hmrc.digitalservicestax.data.{AddressLine, Company, CompanyName, CompanyRegWrapper, ContactDetails, DSTRegNumber, Email, InternalId, PhoneNumber, Postcode, Registration, RestrictiveString, SafeId, UTR, UkAddress}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class FakeIdentifierRegisteredAction @Inject() (bodyParser: PlayBodyParsers)(implicit
  val executionContext: ExecutionContext
) extends RegisteredActionRefiner {

  override protected def refine[A](request: LoggedInRequest[A]): Future[Either[Result, RegisteredRequest[A]]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
    Future.successful(Right(RegisteredRequest(givenRegistration, givenLoggedInRequest(request))))
  }

  private def givenLoggedInRequest[A](request: Request[A]): LoggedInRequest[A] =
    LoggedInRequest(
      internalId,
      Enrolments(
        Set(Enrolment("HMRC-DST-ORG", Seq(EnrolmentIdentifier("DSTRefNumber", "AMDST0799721562")), "Activated"))
      ),
      "provider-id",
      Some("123456"),
      request
    )

}

object FakeIdentifierRegisteredAction {
  val internalId: data.InternalId = InternalId("Int-aaff66")

  val givenRegistration: Registration =
    Registration(
      CompanyRegWrapper(
        Company(
          CompanyName("Test Solutions Ltd"),
          UkAddress(AddressLine("Test Line 1"), None, None, None, Postcode("NW11 4RP"))
        ),
        Some(UTR("1234567890")),
        Some(SafeId("XE0001234567890")),
        useSafeId = true,
        None
      ),
      Some(UkAddress(AddressLine("Test Line 2"), None, None, None, Postcode("NW8 5AX"))),
      Some(
        Company(
          CompanyName("Ultimate Test Solutions Ltd"),
          UkAddress(AddressLine("Test Line 3"), None, None, None, Postcode("NW11 4XP"))
        )
      ),
      ContactDetails(
        RestrictiveString("Tom"),
        RestrictiveString("Riddle"),
        PhoneNumber("07340507800"),
        Email("tom.riddle@gmail.com")
      ),
      LocalDate.now().minusWeeks(50),
      LocalDate.now().minusWeeks(50),
      Some(DSTRegNumber("AMDST0799721562"))
    )
}
