/*
 * Copyright 2026 HM Revenue & Customs
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

package unit.uk.gov.hmrc.digitalservicestax.services

import cats.instances.future._
import uk.gov.hmrc.digitalservicestax.data.Period.Key
import uk.gov.hmrc.digitalservicestax.data._
import uk.gov.hmrc.digitalservicestax.services.MongoPersistence.RetWrapper
import uk.gov.hmrc.digitalservicestax.services.{MongoPersistence, Persistence}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject._
import scala.concurrent._

@Singleton
class FutureVolatilePersistence @Inject() (implicit ec: ExecutionContext) extends Persistence[Future] {

  val inner = new VolatilePersistence {}

  private def f[A](in: A): Future[A] = Future.successful(in)

  val pendingCallbacks = new PendingCallbacks {
    private def V                                                    = inner.pendingCallbacks
    def get(formBundle: FormBundleNumber)                            = f(V.get(formBundle))
    def delete(formBundle: FormBundleNumber)                         = f(V.delete(formBundle))
    def update(formBundle: FormBundleNumber, internalId: InternalId) = f(V.update(formBundle, internalId))
    def reverseLookup(id: InternalId)                                = f(V.reverseLookup(id))

    override def repository(): PlayMongoRepository[MongoPersistence.CallbackWrapper] =
      inner.pendingCallbacks.repository()
  }

  val registrations = new Registrations {
    private def V = inner.registrations

    def get(user: InternalId): Future[Option[Registration]] = f(V.get(user))

    def update(user: InternalId, reg: Registration): Future[Unit] =
      f(V.update(user, reg))

    override def confirm(user: InternalId, newRegNo: DSTRegNumber): Future[Registration] =
      f(V.confirm(user, newRegNo))

    override def findByRegistrationNumber(registrationNumber: DSTRegNumber): Future[Option[Registration]] = f(
      V.findByRegistrationNumber(registrationNumber)
    )

    override def findWrapperByRegistrationNumber(
      registrationNumber: DSTRegNumber
    ): Future[Option[MongoPersistence.RegWrapper]] = f(
      V.findWrapperByRegistrationNumber(registrationNumber)
    )

    override def delete(registrationNumber: DSTRegNumber): Future[Long] = f(V.delete(registrationNumber))

    override def findBySafeId(safeId: SafeId): Future[Option[MongoPersistence.RegWrapper]] = f(V.findBySafeId(safeId))

    override def findByEmail(email: Email): Future[Option[MongoPersistence.RegWrapper]] = f(V.findByEmail(email))

    override def repository(): PlayMongoRepository[MongoPersistence.RegWrapper] = inner.registrations.repository()
  }

  val returns = new Returns {
    private def V                                                                = inner.returns
    def get(reg: Registration): Future[Map[Key, Return]]                         = f(V.get(reg))
    def update(reg: Registration, period: Period.Key, ret: Return): Future[Unit] =
      f(V.update(reg, period, ret))

    override def repository(): PlayMongoRepository[RetWrapper] = inner.returns.repository()
  }

}
