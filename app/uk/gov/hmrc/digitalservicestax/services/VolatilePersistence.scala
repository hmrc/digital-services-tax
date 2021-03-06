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

package uk.gov.hmrc.digitalservicestax
package services

import cats.Id
import data._
import java.time.{LocalDate, LocalDateTime}

import cats.implicits._
import uk.gov.hmrc.digitalservicestax.data.Period.Key

trait VolatilePersistence extends Persistence[Id] {

  val pendingCallbacks = new PendingCallbacks {

    @volatile private var _data: Map[FormBundleNumber, InternalId] = Map.empty
    def get(formBundle: FormBundleNumber): Option[InternalId] = _data.get(formBundle)
    def delete(formBundle: FormBundleNumber) = _data = _data - formBundle
    def update(formBundle: FormBundleNumber, internalId: InternalId) =
      _data = _data + (formBundle -> internalId)
    def reverseLookup(id: InternalId): Option[FormBundleNumber] =
      _data.collectFirst{ case (k, v) if v == id => k }
    override def insert(formBundleNumber: FormBundleNumber, internalId: InternalId): Id[Unit] = {
      _data = _data + (formBundleNumber -> internalId)
    }
  }

  def randomDstNumber: DSTRegNumber = {
    val r = new scala.util.Random()
    def c: Char = {65 + r.nextInt.abs % (90 - 64)}.toChar
    def digits: String = f"${r.nextInt.abs}%010d"
    DSTRegNumber(s"${c}${c}DST$digits")
  }

  val registrations = new Registrations {

    @volatile private var _data: Map[InternalId, (Registration, LocalDateTime)] = Map.empty

    override def insert(
      user: InternalId,
      reg: Registration
    ): Id[Unit] = {
      _data = _data + (user -> ((reg, LocalDateTime.now)))
    }

    val fixedDstNumber = randomDstNumber
    def get(user: InternalId) = {
      _data.get(user) match {
        case Some((r,d)) if r.registrationNumber.isEmpty && d.plusMinutes(1).isBefore(LocalDateTime.now) =>
          update(user, r.copy(registrationNumber = Some(randomDstNumber)))
          get(user)
        case x => x.map{_._1}
      }
    }

    def update(user: InternalId, reg: Registration): Unit = 
      _data = _data + (user -> ((reg, LocalDateTime.now)))

  }

  val returns = new Returns {

    @volatile private var _data: Map[Registration, Map[Period.Key, Return]] =
      Map.empty

    def get(reg: Registration): Map[Period.Key,Return] = _data(reg)

    def update(reg: Registration, all: Map[Period.Key, Return]): Unit = {
      _data = _data + (reg -> all)
    }

    def update(reg: Registration, period: Period.Key, ret: Return): Unit = {
      val updatedMap = {
        val existing = _data.getOrElse(reg, Map.empty[Period.Key, Return])
        existing + (period -> ret)
      }
      update(reg, updatedMap)
    }

    override def insert(reg: Registration, key: Key, ret: Return): Id[Unit] = {
      _data = _data + (reg -> Map(key -> ret))
    }
  }
}
