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

package uk.gov.hmrc.digitalservicestax
package services

import cats.implicits._
import uk.gov.hmrc.digitalservicestax.data._

import scala.concurrent.Future
import scala.language.higherKinds

abstract class Persistence[F[_]: cats.Monad] {

  protected trait PendingCallbacks {
    def apply(formBundle: FormBundleNumber): F[InternalId] = get(formBundle).map{
      _.getOrElse(throw new NoSuchElementException(s"formBundle not found: $formBundle"))
    }

    def get(formBundle: FormBundleNumber): F[Option[InternalId]]
    def reverseLookup(id: InternalId): F[Option[FormBundleNumber]]
    def delete(formBundle: FormBundleNumber): F[Unit]
    def update(formBundle: FormBundleNumber, internalId: InternalId): F[Unit]
    def process(formBundle: FormBundleNumber, regId: DSTRegNumber): F[Registration] =
      {apply(formBundle) >>= (registrations.confirm(_, regId))} <* delete(formBundle)
  }

  def pendingCallbacks: PendingCallbacks

  protected trait Registrations {
    def apply(user: InternalId): F[Registration] = get(user).map {
      _.getOrElse(throw new NoSuchElementException(s"user not found: $user"))
    }

    def get(user: InternalId): F[Option[Registration]]
    def update(user: InternalId, reg: Registration): F[Unit]

    def confirm(user: InternalId, registrationNumber: DSTRegNumber): F[Registration] =
      for {
        existing <- apply(user)
        updated  = existing.copy(registrationNumber = Some(registrationNumber))
        _        <- update(user, updated)
      } yield (updated)
  }

  def registrations: Registrations

  protected trait Returns {
    def apply(reg: Registration): F[Map[Period.Key, Return]] = get(reg)
    def apply(reg: Registration, periodKey: Period.Key): F[Return] = get(reg, periodKey).map{
      _.getOrElse(throw new NoSuchElementException(s"return not found: $reg/$periodKey"))
    }

    def get(reg: Registration): F[Map[Period.Key, Return]]
    def get(reg: Registration, period: Period.Key): F[Option[Return]] =
      get(reg).map{_.get(period)}

    def update(reg: Registration, period: Period.Key, ret: Return): F[Unit]

  }

  def returns: Returns

}

