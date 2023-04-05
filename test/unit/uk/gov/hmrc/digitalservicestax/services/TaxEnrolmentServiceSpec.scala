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

package unit.uk.gov.hmrc.digitalservicestax.services

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors
import uk.gov.hmrc.digitalservicestax.connectors.{Identifier, TaxEnrolmentConnector, TaxEnrolmentsSubscription}
import uk.gov.hmrc.digitalservicestax.data.{DSTRegNumber, InternalId, Registration}
import uk.gov.hmrc.digitalservicestax.services.TaxEnrolmentService
import unit.uk.gov.hmrc.digitalservicestax.util.FakeApplicationSetup
import unit.uk.gov.hmrc.digitalservicestax.util.TestInstances._

import scala.concurrent.Future

class TaxEnrolmentServiceSpec
    extends FakeApplicationSetup
    with OptionValues
    with BeforeAndAfterEach
    with ScalaCheckDrivenPropertyChecks {

  val mockTaxEnrolmentsConnector: TaxEnrolmentConnector = mock[connectors.TaxEnrolmentConnector]
  val mockAppConfig: AppConfig                          = mock[AppConfig]
  val dstService                                        = new TaxEnrolmentService(mockAppConfig, mockTaxEnrolmentsConnector, mongoPersistence)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockTaxEnrolmentsConnector, mockAppConfig)
  }

  "Tax enrolments service" should {

    "return DstRegNumber when tax enrolments connector returns taxEnrolmentsSubscription" in {

      val registration                                         = arbitrary[Registration].sample.value
      val internal                                             = arbitrary[InternalId].sample.value
      val dstNumber                                            = registration.registrationNumber.getOrElse(DSTRegNumber("XYDST0000000000"))
      val taxEnrolmentsSubscription: TaxEnrolmentsSubscription =
        TaxEnrolmentsSubscription(Some(Seq(Identifier("DSTRefNumber", dstNumber))), "SUCCEEDED", None)

      when(mockAppConfig.dstNewSolutionFeatureFlag).thenReturn(true)

      when(mockTaxEnrolmentsConnector.getSubscriptionByGroupId(any())(any(), any())) thenReturn Future.successful(
        Some(taxEnrolmentsSubscription)
      )
      val chain = for {
        r <- mongoPersistence.registrations.update(internal, registration)
      } yield r

      whenReady(chain) {_ =>
        val result: Registration = dstService.getDSTRegistration(Some("1234")).futureValue.value
        result mustBe registration
      }

    }

    "return 'None' when tax enrolments connector does not return DstRegNumber" in {
      val taxEnrolmentsSubscription: TaxEnrolmentsSubscription =
        TaxEnrolmentsSubscription(Some(Seq(Identifier("Dummy", "DummyValue"))), "SUCCEEDED", None)
      when(mockAppConfig.dstNewSolutionFeatureFlag).thenReturn(true)

      when(mockTaxEnrolmentsConnector.getSubscriptionByGroupId(any())(any(), any())) thenReturn Future.successful(
        Some(taxEnrolmentsSubscription)
      )

      val result = dstService.getDSTRegistration(Some("1234"))
      result.futureValue mustBe None

    }

    "return 'None' when tax enrolments connector return error message" in {
      val taxEnrolmentsSubscription: TaxEnrolmentsSubscription = TaxEnrolmentsSubscription(None, "ERROR", Some("error"))
      when(mockAppConfig.dstNewSolutionFeatureFlag).thenReturn(true)

      when(mockTaxEnrolmentsConnector.getSubscriptionByGroupId(any())(any(), any())) thenReturn Future.successful(
        Some(taxEnrolmentsSubscription)
      )

      val result = dstService.getDSTRegistration(Some("1234"))
      result.futureValue mustBe None
    }

    "return 'None' when dstNewSolutionFeatureFlag is false" in {
      when(mockAppConfig.dstNewSolutionFeatureFlag).thenReturn(false)

      val result = dstService.getDSTRegistration(Some("1234"))
      result.futureValue mustBe None
    }
  }
}
