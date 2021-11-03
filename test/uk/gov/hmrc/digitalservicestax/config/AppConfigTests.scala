/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.digitalservicestax.config

import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

import org.scalatest.{FlatSpec, Matchers}
import uk.gov.hmrc.digitalservicestax.util.TestWiring

class AppConfigTests extends FlatSpec with Matchers with TestWiring {

  it should "read a non empty authbase URL from app config" in {
    appConfig.authBaseUrl.isEmpty shouldEqual false
  }

  it should "read a non empty auditingEnabled flag from app config" in {
    appConfig.auditingEnabled shouldEqual false
  }

  it should "read a non empty graphiteHost flag from app config" in {
    appConfig.graphiteHost.nonEmpty shouldEqual true
  }


  it should "read an obligation start date from app config" in {
    val pattern = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    appConfig.obligationStartDate.nonEmpty shouldEqual true

    val dt = pattern.parse(appConfig.obligationStartDate)
    dt shouldBe a [TemporalAccessor]
  }

}
