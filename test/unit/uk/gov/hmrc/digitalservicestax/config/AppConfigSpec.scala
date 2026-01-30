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

package unit.uk.gov.hmrc.digitalservicestax.config

import org.scalatest.matchers.should.Matchers.*
import unit.uk.gov.hmrc.digitalservicestax.util.FakeApplicationSetup

import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

class AppConfigSpec extends FakeApplicationSetup {

  "should read a non empty authbase URL from app config" in {
    appConfig.authBaseUrl.isEmpty shouldEqual false
  }

  "should read a non empty auditingEnabled flag from app config" in {
    appConfig.auditingEnabled shouldEqual false
  }

  "should read a non empty graphiteHost flag from app config" in {
    appConfig.graphiteHost.nonEmpty shouldEqual true
  }

  "should read an obligation start date from app config" in {
    val pattern = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    appConfig.obligationStartDate.nonEmpty shouldEqual true

    val dt = pattern.parse(appConfig.obligationStartDate)
    dt shouldBe a[TemporalAccessor]
  }

  "should load the feature flag dstNewSolutionFeatureFlag" in {
    appConfig.dstNewSolutionFeatureFlag mustEqual true
  }

}
