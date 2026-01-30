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

package unit.uk.gov.hmrc.digitalservicestax.services

import org.mockito.ArgumentMatchers.{any, same}
import org.mockito.Mockito.{reset, when}
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.digitalservicestax.backend_data.{SubscriptionStatus, SubscriptionStatusResponse}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.connectors.{EnrolmentStoreProxyConnector, RegistrationConnector, RosmConnector, TaxEnrolmentConnector}
import uk.gov.hmrc.digitalservicestax.data.{CompanyRegWrapper, DSTRegNumber, InternalId, Registration, SapNumber, UTR}
import uk.gov.hmrc.digitalservicestax.services.GetDstNumberFromEisService
import unit.uk.gov.hmrc.digitalservicestax.util.FakeApplicationSetup
import unit.uk.gov.hmrc.digitalservicestax.util.TestInstances._

import scala.concurrent.Future

class GetDstNumberFromEisServiceSpec
    extends FakeApplicationSetup
    with OptionValues
    with BeforeAndAfterEach
    with ScalaCheckDrivenPropertyChecks {

  val mockTaxEnrolmentsConnector: TaxEnrolmentConnector              = mock[TaxEnrolmentConnector]
  val mockAppConfig: AppConfig                                       = mock[AppConfig]
  val mockRosmConnector: RosmConnector                               = mock[RosmConnector]
  val mockRegistrationConnector: RegistrationConnector               = mock[RegistrationConnector]
  val mockEnrolmentStoreProxyConnector: EnrolmentStoreProxyConnector = mock[EnrolmentStoreProxyConnector]

  val getDstNumberFromEisService: GetDstNumberFromEisService = new GetDstNumberFromEisService(
    mockAppConfig,
    mockRosmConnector,
    mockRegistrationConnector,
    mongoPersistence,
    mockTaxEnrolmentsConnector,
    mockEnrolmentStoreProxyConnector
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(
      mockTaxEnrolmentsConnector,
      mockAppConfig,
      mockRosmConnector,
      mockRegistrationConnector,
      mockEnrolmentStoreProxyConnector
    )
  }

  "GetDstNumberFromEis service" should {

    "return registration details when all details BPR, subscription status exists and successful and enrolment activation is successful" in {

      val registration                                   = arbitrary[Registration].sample.value
      val companyRegWrapper                              = arbitrary[CompanyRegWrapper].sample.value.copy(sapNumber = Some(SapNumber("1234567890")))
      val utr                                            = arbitrary[UTR].sample.value
      val internal                                       = arbitrary[InternalId].sample.value
      val dstNumber                                      = registration.registrationNumber.getOrElse(DSTRegNumber("XYDST0000000000"))
      val subscriptionStatus: SubscriptionStatusResponse =
        SubscriptionStatusResponse(SubscriptionStatus.Subscribed, Some("ZDST"), Some(dstNumber.value))

      when(mockAppConfig.dstNewSolutionFeatureFlag).thenReturn(true)

      when(mockRosmConnector.retrieveROSMDetails(same(utr))(any(), any())) thenReturn Future.successful(
        Some(companyRegWrapper)
      )
      when(
        mockRegistrationConnector
          .getSubscriptionStatus(same(SapNumber("1234567890")))(any(), any())
      ) thenReturn Future.successful(subscriptionStatus)

      when(
        mockTaxEnrolmentsConnector.isAllocateDstGroupEnrolmentSuccess(any(), same(dstNumber.value))(any(), any())
      ) thenReturn Future.successful(true)

      when(
        mockEnrolmentStoreProxyConnector.getDstRefFromGroupAssignedEnrolment(same("groupId"))(any(), any())
      ) thenReturn Future.successful(Some(dstNumber))

      val chain = for {
        r <- mongoPersistence.registrations.update(internal, registration)
      } yield r

      whenReady(chain) { _ =>
        val result: Option[Registration] = getDstNumberFromEisService
          .getDstNumberAndActivateEnrolment(utr, "groupId")
          .futureValue
        result mustBe Some(registration)
      }

    }

    "return None when BPR, subscription status exists and successful and enrolment activation fails" in {

      val registration                                   = arbitrary[Registration].sample.value
      val companyRegWrapper                              = arbitrary[CompanyRegWrapper].sample.value.copy(sapNumber = Some(SapNumber("1234567890")))
      val utr                                            = arbitrary[UTR].sample.value
      val internal                                       = arbitrary[InternalId].sample.value
      val dstNumber                                      = registration.registrationNumber.getOrElse(DSTRegNumber("XYDST0000000000"))
      val subscriptionStatus: SubscriptionStatusResponse =
        SubscriptionStatusResponse(SubscriptionStatus.Subscribed, Some("ZDST"), Some(dstNumber.value))

      when(mockAppConfig.dstNewSolutionFeatureFlag).thenReturn(true)

      when(mockRosmConnector.retrieveROSMDetails(same(utr))(any(), any())) thenReturn Future.successful(
        Some(companyRegWrapper)
      )
      when(
        mockRegistrationConnector
          .getSubscriptionStatus(same(SapNumber("1234567890")))(any(), any())
      ) thenReturn Future.successful(subscriptionStatus)

      when(
        mockTaxEnrolmentsConnector.isAllocateDstGroupEnrolmentSuccess(any(), same(dstNumber.value))(any(), any())
      ) thenReturn Future.successful(false)

      when(
        mockEnrolmentStoreProxyConnector.getDstRefFromGroupAssignedEnrolment(same("groupId"))(any(), any())
      ) thenReturn Future.successful(Some(dstNumber))

      val chain = for {
        r <- mongoPersistence.registrations.update(internal, registration)
      } yield r

      whenReady(chain) { _ =>
        val result: Option[Registration] = getDstNumberFromEisService
          .getDstNumberAndActivateEnrolment(utr, "groupId")
          .futureValue
        result mustBe None
      }

    }

    "return None when BPR or SapNumber does not exists" in {

      val registration      = arbitrary[Registration].sample.value
      val companyRegWrapper = arbitrary[CompanyRegWrapper].sample.value.copy(sapNumber = None)
      val utr               = arbitrary[UTR].sample.value
      val internal          = arbitrary[InternalId].sample.value

      when(mockAppConfig.dstNewSolutionFeatureFlag).thenReturn(true)

      when(mockRosmConnector.retrieveROSMDetails(same(utr))(any(), any())) thenReturn Future.successful(
        Some(companyRegWrapper)
      )

      val chain = for {
        r <- mongoPersistence.registrations.update(internal, registration)
      } yield r

      whenReady(chain) { _ =>
        val result: Option[Registration] = getDstNumberFromEisService
          .getDstNumberAndActivateEnrolment(utr, "groupId")
          .futureValue
        result mustBe None
      }
    }

    "return None when BPR exists but subscription status is not successful" in {

      val registration                                   = arbitrary[Registration].sample.value
      val companyRegWrapper                              = arbitrary[CompanyRegWrapper].sample.value.copy(sapNumber = Some(SapNumber("1234567890")))
      val utr                                            = arbitrary[UTR].sample.value
      val internal                                       = arbitrary[InternalId].sample.value
      val dstNumber                                      = registration.registrationNumber.getOrElse(DSTRegNumber("XYDST0000000000"))
      val subscriptionStatus: SubscriptionStatusResponse =
        SubscriptionStatusResponse(SubscriptionStatus.Other, Some("ZDST"), Some(dstNumber.value))

      when(mockAppConfig.dstNewSolutionFeatureFlag).thenReturn(true)

      when(mockRosmConnector.retrieveROSMDetails(same(utr))(any(), any())) thenReturn Future.successful(
        Some(companyRegWrapper)
      )
      when(
        mockRegistrationConnector
          .getSubscriptionStatus(same(SapNumber("1234567890")))(any(), any())
      ) thenReturn Future.successful(subscriptionStatus)

      val chain = for {
        r <- mongoPersistence.registrations.update(internal, registration)
      } yield r

      whenReady(chain) { _ =>
        val result: Option[Registration] = getDstNumberFromEisService
          .getDstNumberAndActivateEnrolment(utr, "groupId")
          .futureValue
        result mustBe None
      }

    }
    "return None when BPR, subscription status exists and successful and enrolment activation is successful but eacd returned different DstRegNumber" in {

      val registration                                   = arbitrary[Registration].sample.value
      val companyRegWrapper                              = arbitrary[CompanyRegWrapper].sample.value.copy(sapNumber = Some(SapNumber("1234567890")))
      val utr                                            = arbitrary[UTR].sample.value
      val internal                                       = arbitrary[InternalId].sample.value
      val dstNumber                                      = registration.registrationNumber.getOrElse(DSTRegNumber("XYDST0000000000"))
      val subscriptionStatus: SubscriptionStatusResponse =
        SubscriptionStatusResponse(SubscriptionStatus.Subscribed, Some("ZDST"), Some(dstNumber.value))

      when(mockAppConfig.dstNewSolutionFeatureFlag).thenReturn(true)

      when(mockRosmConnector.retrieveROSMDetails(same(utr))(any(), any())) thenReturn Future.successful(
        Some(companyRegWrapper)
      )
      when(
        mockRegistrationConnector
          .getSubscriptionStatus(same(SapNumber("1234567890")))(any(), any())
      ) thenReturn Future.successful(subscriptionStatus)

      when(
        mockTaxEnrolmentsConnector.isAllocateDstGroupEnrolmentSuccess(any(), same(dstNumber.value))(any(), any())
      ) thenReturn Future.successful(true)

      when(
        mockEnrolmentStoreProxyConnector.getDstRefFromGroupAssignedEnrolment(same("groupId"))(any(), any())
      ) thenReturn Future.successful(Some("XYDST0000000001"))

      val chain = for {
        r <- mongoPersistence.registrations.update(internal, registration)
      } yield r

      whenReady(chain) { _ =>
        val result: Option[Registration] = getDstNumberFromEisService
          .getDstNumberAndActivateEnrolment(utr, "groupId")
          .futureValue
        result mustBe None
      }

    }

    "return None when BPR exists but subscription id is not DST" in {

      val registration                                   = arbitrary[Registration].sample.value
      val companyRegWrapper                              = arbitrary[CompanyRegWrapper].sample.value.copy(sapNumber = Some(SapNumber("1234567890")))
      val utr                                            = arbitrary[UTR].sample.value
      val internal                                       = arbitrary[InternalId].sample.value
      val dstNumber                                      = registration.registrationNumber.getOrElse(DSTRegNumber("XYDST0000000000"))
      val subscriptionStatus: SubscriptionStatusResponse =
        SubscriptionStatusResponse(SubscriptionStatus.Subscribed, Some("ZXXX"), Some(dstNumber.value))

      when(mockAppConfig.dstNewSolutionFeatureFlag).thenReturn(true)

      when(mockRosmConnector.retrieveROSMDetails(same(utr))(any(), any())) thenReturn Future.successful(
        Some(companyRegWrapper)
      )
      when(
        mockRegistrationConnector
          .getSubscriptionStatus(same(SapNumber("1234567890")))(any(), any())
      ) thenReturn Future.successful(subscriptionStatus)

      when(
        mockTaxEnrolmentsConnector.isAllocateDstGroupEnrolmentSuccess(any(), same(dstNumber.value))(any(), any())
      ) thenReturn Future.successful(true)

      when(
        mockEnrolmentStoreProxyConnector.getDstRefFromGroupAssignedEnrolment(same("groupId"))(any(), any())
      ) thenReturn Future.successful(Some(dstNumber))

      val chain = for {
        r <- mongoPersistence.registrations.update(internal, registration)
      } yield r

      whenReady(chain) { _ =>
        val result: Option[Registration] = getDstNumberFromEisService
          .getDstNumberAndActivateEnrolment(utr, "groupId")
          .futureValue
        result mustBe None
      }

    }
  }
}
