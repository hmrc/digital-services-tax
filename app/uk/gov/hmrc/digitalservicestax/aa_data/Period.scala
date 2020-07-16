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

package uk.gov.hmrc.digitalservicestax.data

import java.time.LocalDate
import shapeless.tag.@@
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._


case class Period(
                   start: LocalDate,
                   end: LocalDate,
                   returnDue: LocalDate,
                   key: Period.Key) {
  def paymentDue: LocalDate = end.minusMonths(3)
}


object Period {

  type Key = String @@ Key.Tag

  object Key extends ValidatedType[String] {
    def validateAndTransform(in: String): Option[String] =
      Some(in).filter { x => x.nonEmpty && x.size <= 4 }
  }

  def apply(
             start: LocalDate,
             end: LocalDate,
             returnDue: LocalDate,
             key: String): Period = new Period(start, end, returnDue, Period.Key(key))

  def unapply(arg: Period): Option[(LocalDate, LocalDate, LocalDate, Key)] = Option((arg.start, arg.end, arg.returnDue, arg.key))

  val periodForm: Form[Period] = Form(
    mapping(
      "start" -> localDate,
      "end" -> localDate,
      "returnDue" -> localDate,
      "key" -> nonEmptyText
    )(Period.apply)(Period.unapply)
  )


}
