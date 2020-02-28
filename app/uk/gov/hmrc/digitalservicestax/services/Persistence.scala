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

import scala.language.higherKinds

import cats.syntax.functor._
import data._
import java.time.LocalDateTime

abstract class Persistence[F[_]: cats.Functor] {

  protected trait Registrations {
    def apply(user: String): F[Registration] = get(user).map{
      _.getOrElse(throw new NoSuchElementException(s"user not found: $user"))
    }
    def get(user: String): F[Option[Registration]]
    def update(user: String, reg: Registration): F[Unit]
    def confirm(user: String, registrationNumber: DSTRegNumber): F[Unit]
  }

  def registrations: Registrations

  protected trait Returns {
    def apply(reg: Registration): F[Map[Period, Return]] = get(reg)
    def apply(reg: Registration, period: Period): F[Return] = get(reg, period).map{
      _.getOrElse(throw new NoSuchElementException(s"return not found: $reg/$period"))
    }

    def get(reg: Registration): F[Map[Period, Return]]
    def get(reg: Registration, period: Period): F[Option[Return]] =
      get(reg).map{_.get(period)}

    def update(reg: Registration, period: Period, ret: Return): F[Unit]
    def update(reg: Registration, all: Map[Period,Return]): F[Unit]
  }

  def returns: Returns

}

