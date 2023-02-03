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

package it.uk.gov.hmrc.digitalservicestax.controllers

import it.uk.gov.hmrc.digitalservicestax.helpers.ControllerBaseSpec
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.digitalservicestax.connectors.{Identifier, TaxEnrolmentsSubscription}
import uk.gov.hmrc.digitalservicestax.controllers.TaxEnrolmentController

import scala.concurrent.Future

class TaxEnrolmentControllerSpec extends ControllerBaseSpec {

  object TestTaxEnrolmentController
      extends TaxEnrolmentController(mockAuthConnector, stubControllerComponents(), mockTaxEnrolmentsConnector)

  "Tax enrolments controller" must {

    "return 200 when tax enrolments connecter returns DstRegNumber" in {
      val taxEnrolmentsSubscription: TaxEnrolmentsSubscription = TaxEnrolmentsSubscription(
        Some(Seq(Identifier("DSTRefNumber", "XYDST0000000000"))), "SUCCEEDED", None)

      mockAuth()
      when(mockTaxEnrolmentsConnector.getSubscriptionByGroupId(any())(any(), any())) thenReturn Future.successful(taxEnrolmentsSubscription)

      val result: Future[Result] = TestTaxEnrolmentController.getSubscription("123456").apply(FakeRequest())
      val resultStatus           = status(result)

      resultStatus mustBe 200
      contentAsString(
        result
      ) mustBe """XYDST0000000000"""

    }

    "return 404 when tax enrolments connecter does not return DstRegNumber" in {
      val taxEnrolmentsSubscription: TaxEnrolmentsSubscription = TaxEnrolmentsSubscription(
        Some(Seq(Identifier("Dummy", "DummyValue"))), "SUCCEEDED", None)

      mockAuth()
      when(mockTaxEnrolmentsConnector.getSubscriptionByGroupId(any())(any(), any())) thenReturn Future.successful(taxEnrolmentsSubscription)

      val result: Future[Result] = TestTaxEnrolmentController.getSubscription("123456").apply(FakeRequest())
      val resultStatus           = status(result)

      resultStatus mustBe 404
    }

    "return 500 when tax enrolments connecter return error message" in {
      val taxEnrolmentsSubscription: TaxEnrolmentsSubscription = TaxEnrolmentsSubscription(None, "ERROR", Some("error"))

      mockAuth()
      when(mockTaxEnrolmentsConnector.getSubscriptionByGroupId(any())(any(), any())) thenReturn Future.successful(taxEnrolmentsSubscription)

      val result: Future[Result] = TestTaxEnrolmentController.getSubscription("123456").apply(FakeRequest())
      val resultStatus           = status(result)

      resultStatus mustBe 500
    }

  }
}
