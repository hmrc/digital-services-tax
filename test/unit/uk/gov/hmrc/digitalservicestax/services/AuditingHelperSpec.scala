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

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import uk.gov.hmrc.digitalservicestax.controllers.CallbackNotification
import uk.gov.hmrc.digitalservicestax.data.{DSTRegNumber, FormBundleNumber, Period, Registration, Return}
import uk.gov.hmrc.digitalservicestax.services.AuditingHelper
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import unit.uk.gov.hmrc.digitalservicestax.util.TestInstances._

class AuditingHelperSpec extends AnyFreeSpec with ScalaCheckPropertyChecks with OptionValues {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "AuditingHelper" - {
    "must build Return Submission Audit extended event" in {
      forAll { (sampleReturn: Return, period: Period) =>
        val extendedDataEvent: ExtendedDataEvent =
          AuditingHelper.buildReturnSubmissionAudit(DSTRegNumber("BCDST1234567890"), "", period, sampleReturn, true)

        extendedDataEvent.auditSource mustBe "digital-services-tax"
        extendedDataEvent.auditType mustBe "returnSubmitted"
      }
    }

    "must build Return Response Audit extended event" in {

      val extendedDataEvent: ExtendedDataEvent = AuditingHelper.buildReturnResponseAudit("Success", None)

      extendedDataEvent.auditSource mustBe "digital-services-tax"
      extendedDataEvent.auditType mustBe "returnSubmissionResponse"
    }

    "must build registration audit event" in {
      forAll { (registration: Registration) =>
        val extendedDataEvent: ExtendedDataEvent = AuditingHelper.buildRegistrationAudit(
          registration,
          "providerId",
          Some(FormBundleNumber("112233445566")),
          "SUCCESS"
        )

        extendedDataEvent.auditSource mustBe "digital-services-tax"
        extendedDataEvent.auditType mustBe "digitalServicesTaxRegistrationSubmitted"
      }
    }

    "must build callback audit event" in {

      val notification                         = CallbackNotification("Active", None)
      val extendedDataEvent: ExtendedDataEvent =
        AuditingHelper.buildCallbackAudit(notification, FormBundleNumber("112233445566"), "SUCCESS", None)

      extendedDataEvent.auditSource mustBe "digital-services-tax"
      extendedDataEvent.auditType mustBe "digitalServicesTaxEnrolmentResponse"
    }
  }
}
