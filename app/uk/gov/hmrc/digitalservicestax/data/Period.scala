/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.digitalservicestax.data

import java.time.LocalDate

import shapeless.tag.@@

case class Period(start: LocalDate, end: LocalDate, returnDue: LocalDate, key: Period.Key) {
  def paymentDue: LocalDate =
    end match {
      case e if e.getDayOfMonth == e.lengthOfMonth() => end.plusMonths(10).withDayOfMonth(1)
      case _                                         => end.plusMonths(9).plusDays(1)
    }
}

object Period {

  type Key = String @@ Key.Tag
  object Key extends ValidatedType[String] {
    def validateAndTransform(in: String): Option[String] =
      Some(in)
        .filter(x => x.nonEmpty && x.size <= 4)
        .map("^#".r.replaceAllIn(_, ""))
  }

}
