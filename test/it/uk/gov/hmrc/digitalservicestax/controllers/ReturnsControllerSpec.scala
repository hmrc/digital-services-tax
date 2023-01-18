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

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.digitalservicestax.controllers.ReturnsController
import uk.gov.hmrc.digitalservicestax.data.Period
import it.uk.gov.hmrc.digitalservicestax.helpers.ControllerBaseSpec

import java.time.LocalDate
import scala.concurrent.Future

class ReturnsControllerSpec extends ControllerBaseSpec {

  object TestReturnsController
      extends ReturnsController(
        authConnector = mockAuthConnector,
        runModeConfiguration = mockRunModeConfiguration,
        cc = stubControllerComponents(),
        persistence = mockPersistence,
        connector = mockConnector,
        auditing = mockAuditing,
        registered = mockRegistered,
        loggedIn = loginReturn
      )

  private val startAmendable1 = LocalDate.now().minusYears(2).minusWeeks(1)
  private val endAmendable1   = startAmendable1.plusYears(1)
  private val dueAmendable1   = endAmendable1.plusYears(1).minusWeeks(1)

  private val startAmendable2 = LocalDate.now().minusYears(3)
  private val endAmendable2   = startAmendable2.plusYears(1)
  private val dueAmendable2   = endAmendable2.plusYears(1).plusDays(1)

  private val startNotAmendable = LocalDate.now().minusYears(4).minusWeeks(1)
  private val endNotAmendable   = startNotAmendable.plusYears(1)
  private val dueNotAmendable   = endNotAmendable.plusYears(1)

  "Amendable Returns" must {

    "return 200 with 1 period within time frame" in {

      val periods: Future[List[(Period, Option[LocalDate])]] =
        Future.successful(
          List(
            Period(startAmendable1, endAmendable1, dueAmendable1, Period.Key("001")) -> Some(dueAmendable1.minusDays(5))
          )
        )

      when(mockConnector.getPeriods(any())(any(), any())) thenReturn periods

      val result: Future[Result] = TestReturnsController.lookupAmendableReturns().apply(FakeRequest())
      val resultStatus           = status(result)

      resultStatus mustBe 200
      contentAsString(
        result
      ) mustBe s"""[{"start":"$startAmendable1","end":"$endAmendable1","returnDue":"$dueAmendable1","key":"001"}]"""

    }

    "return 200 with no periods within time frame" in {

      val periods: Future[List[(Period, Option[LocalDate])]] =
        Future.successful(
          List(
            Period(startNotAmendable, endNotAmendable, dueNotAmendable, Period.Key("001")) -> Some(
              dueNotAmendable.minusDays(5)
            )
          )
        )

      when(mockConnector.getPeriods(any())(any(), any())) thenReturn periods

      val result: Future[Result] = TestReturnsController.lookupAmendableReturns().apply(FakeRequest())
      val resultStatus           = status(result)

      resultStatus mustBe 200
      contentAsString(result) mustBe s"""[]"""

    }

    "return 200 with 2 periods within time frame" in {

      val periodList: List[(Period, Option[LocalDate])] = List(
        Period(startAmendable1, endAmendable1, dueAmendable1, Period.Key("001"))       -> Some(dueAmendable1.minusDays(5)),
        Period(startNotAmendable, endNotAmendable, dueNotAmendable, Period.Key("002")) -> Some(
          dueNotAmendable.minusDays(5)
        ),
        Period(startAmendable2, endAmendable2, dueAmendable2, Period.Key("003"))       -> Some(dueAmendable2.minusDays(5))
      )

      val periods: Future[List[(Period, Option[LocalDate])]] =
        Future.successful(periodList)

      when(mockConnector.getPeriods(any())(any(), any())) thenReturn periods

      val result: Future[Result] = TestReturnsController.lookupAmendableReturns().apply(FakeRequest())
      val resultStatus           = status(result)

      resultStatus mustBe 200
      contentAsString(
        result
      ) mustBe s"""[{"start":"$startAmendable1","end":"$endAmendable1","returnDue":"$dueAmendable1","key":"001"},{"start":"$startAmendable2","end":"$endAmendable2","returnDue":"$dueAmendable2","key":"003"}]"""

    }
  }

  "AllReturns" must {
    "return 200 with 3 periods within time frame" in {

      val periodList: List[(Period, Option[LocalDate])] = List(
        Period(startAmendable1, endAmendable1, dueAmendable1, Period.Key("001"))       -> Some(dueAmendable1.minusDays(5)),
        Period(startNotAmendable, endNotAmendable, dueNotAmendable, Period.Key("002")) -> Some(
          dueNotAmendable.minusDays(5)
        ),
        Period(startAmendable2, endAmendable2, dueAmendable2, Period.Key("003"))       -> Some(dueAmendable2.minusDays(5))
      )

      val periods: Future[List[(Period, Option[LocalDate])]] =
        Future.successful(periodList)

      when(mockConnector.getPeriods(any())(any(), any())) thenReturn periods

      val result: Future[Result] = TestReturnsController.lookupAllReturns().apply(FakeRequest())
      val resultStatus           = status(result)

      resultStatus mustBe 200
      contentAsString(
        result
      ) mustBe s"""[{"start":"$startAmendable1","end":"$endAmendable1","returnDue":"$dueAmendable1","key":"001"},{"start":"$startNotAmendable","end":"$endNotAmendable","returnDue":"$dueNotAmendable","key":"002"},{"start":"$startAmendable2","end":"$endAmendable2","returnDue":"$dueAmendable2","key":"003"}]"""

    }
  }

  "OutstandingReturns" must {
    "return 200 with 1 periods within time frame" in {

      val periodList: List[(Period, Option[LocalDate])] = List(
        Period(startAmendable1, endAmendable1, dueAmendable1, Period.Key("001"))       -> Some(dueAmendable1.minusDays(5)),
        Period(startNotAmendable, endNotAmendable, dueNotAmendable, Period.Key("002")) -> None,
        Period(startAmendable2, endAmendable2, dueAmendable2, Period.Key("003"))       -> Some(dueAmendable2.minusDays(5))
      )

      val periods: Future[List[(Period, Option[LocalDate])]] =
        Future.successful(periodList)

      when(mockConnector.getPeriods(any())(any(), any())) thenReturn periods

      val result: Future[Result] = TestReturnsController.lookupOutstandingReturns().apply(FakeRequest())
      val resultStatus           = status(result)

      resultStatus mustBe 200
      contentAsString(
        result
      ) mustBe s"""[{"start":"$startNotAmendable","end":"$endNotAmendable","returnDue":"$dueNotAmendable","key":"002"}]"""

    }
  }

}
