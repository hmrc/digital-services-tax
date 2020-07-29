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
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.digitalservicestax.Helpers.ControllerBaseSpec
import uk.gov.hmrc.digitalservicestax.data.Period

import scala.concurrent.Future


class ReturnsControllerSpec extends ControllerBaseSpec {

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
    val startValid1 = LocalDate.now().minusYears(2).minusWeeks(1)
    val endValid1 = LocalDate.now().minusYears(1).minusWeeks(1)
    val dueValid1 = LocalDate.now().minusWeeks(1)
    val submittedValid1 = LocalDate.now().minusWeeks(50)
    val startFailed = LocalDate.now().minusYears(4).minusWeeks(1)
    val endFailed = startFailed.plusYears(1)
    val dueFailed = endFailed.plusYears(1)
    val submittedFailed = LocalDate.now().minusYears(1)


    "return 200 with 1 period within time frame" in {


      val periods: Future[List[(Period, Option[LocalDate])]] =
        Future.successful(List(Period(
          startValid1,
          endValid1,
          dueValid1,
          Period.Key("001")) ->
          Some(submittedValid1)))

      when(mockConnector.getPeriods(any())(any(), any())) thenReturn periods

      val result: Future[Result] = TestReturnsController.lookupAmendableReturns().apply(FakeRequest())


      val resultStatus = status(result)


      resultStatus mustBe 200
      contentAsString(result) mustBe s"""[{"start":"$startValid1","end":"$endValid1","returnDue":"$dueValid1","key":"001"}]"""

    }

    "return 200 with no periods within time frame" in {


      val periods: Future[List[(Period, Option[LocalDate])]] =
        Future.successful(List(Period(
          startFailed,
          endFailed,
          dueFailed,
          Period.Key("001")) ->
          Some(submittedFailed)))

      when(mockConnector.getPeriods(any())(any(), any())) thenReturn periods

      val result: Future[Result] = TestReturnsController.lookupAmendableReturns().apply(FakeRequest())


      val resultStatus = status(result)


      resultStatus mustBe 200
      contentAsString(result) mustBe s"""[]"""

    }

    "return 200 with 2 periods within time frame" in {

      val startValid2 = LocalDate.now().minusYears(3).minusWeeks(1)
      val endValid2 = startValid2.plusYears(1)
      val dueValid2 = endValid2.plusYears(1)
      val submittedValid2 = LocalDate.now().minusWeeks(1)

      val periodList: List[(Period, Option[LocalDate])] = List(
        Period(startValid1, endValid1, dueValid1, Period.Key("001")) -> Some(submittedValid1),
        Period(startFailed, endFailed, dueFailed, Period.Key("002")) -> Some(submittedFailed),
        Period(startValid2, endValid2, dueValid2, Period.Key("003")) -> Some(submittedValid2))


      val periods: Future[List[(Period, Option[LocalDate])]] =
        Future.successful(periodList)

      when(mockConnector.getPeriods(any())(any(), any())) thenReturn periods

      val result: Future[Result] = TestReturnsController.lookupAmendableReturns().apply(FakeRequest())


      val resultStatus = status(result)


      resultStatus mustBe 200
      contentAsString(result) mustBe s"""[{"start":"$startValid1","end":"$endValid1","returnDue":"$dueValid1","key":"001"},{"start":"$startValid2","end":"$endValid2","returnDue":"$dueValid2","key":"003"}]"""

    }

  }


}
