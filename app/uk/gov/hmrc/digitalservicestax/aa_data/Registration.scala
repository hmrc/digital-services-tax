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

package uk.gov.hmrc.digitalservicestax
package data

//import services._

import java.time.LocalDate

case class Registration (
  utr: Option[UTR],
  useSafeId: Boolean,
  company: Company,
  alternativeContact: Option[Address],
  ultimateParent: Option[Company],
  contact: ContactDetails,
  dateLiable: LocalDate,
  accountingPeriodEnd: LocalDate
) {

  require(!dateLiable.isBefore(Period.firstPeriodStart))

  def period(year: Int): Option[Period] = {
    val start = Period.firstPeriodStart
    if (year < dateLiable.getYear) None
    else {
      Some(Period(
        if (year == start.getYear) {
          dateLiable
        } else {
          start.plusYears(year - start.getYear)
        },
        start.plusYears(1 + year - start.getYear).minusDays(1)
      ))
    }
  }


}
