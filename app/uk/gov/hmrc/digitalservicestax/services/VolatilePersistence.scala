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
package services

import cats.Id
import data._

trait VolatilePersistence extends Persistence[Id] {

  val registrations = new Registrations {

    @volatile private var _data: Map[String, Registration] =
      Map.empty

    def get(user: String) = _data.get(user)

    def update(user: String, reg: Registration): Unit = {
      _data = _data + (user -> reg)
    }

    def confirm(user: String, newRegNo: DSTRegNumber): Unit = {
      update(user, apply(user).copy(registrationNumber = Some(newRegNo)))
    }
  }

  val returns = new Returns {

    @volatile private var _data: Map[Registration, Map[Period, Return]] =
      Map.empty.withDefault(Map.empty)

    def get(reg: Registration): Map[Period,Return] = _data(reg).toMap

    def update(reg: Registration, all: Map[Period,Return]): Unit = {
      _data = _data + (reg -> all)
    }

    def update(reg: Registration, period: Period, ret: Return): Unit = {
      val updatedMap = _data(reg) + (period -> ret)
      update(reg, updatedMap)
    }
  }
}
