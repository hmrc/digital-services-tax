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

import data._
import scala.concurrent._
import cats.instances.future._
import javax.inject._
import java.time.LocalTime
import akka.actor._
import scala.concurrent.duration._

@Singleton
class FutureVolatilePersistence @Inject()(actorSystem: ActorSystem)(implicit ec: ExecutionContext) extends Persistence[Future] {

  val inner = new VolatilePersistence {}

  private def f[A](in: A): Future[A] = Future.successful(in)

  val pendingCallbacks = new PendingCallbacks {
    private def V = inner.pendingCallbacks    
    def get(formBundle: FormBundleNumber) = f(V.get(formBundle))
    def delete(formBundle: FormBundleNumber) = f(V.delete(formBundle))
    def update(formBundle: FormBundleNumber, internalId: String) = f(V.update(formBundle, internalId))
  }

  val registrations = new Registrations {
    private def V = inner.registrations

    def get(user: String) = f(V.get(user))

    def update(user: String, reg: Registration) =
      f(V.update(user, reg))      

    override def confirm(user: String, newRegNo: DSTRegNumber) =
      f(V.confirm(user, newRegNo))

  }

  val returns = new Returns {
    private def V = inner.returns
    def get(reg: Registration) = f(V.get(reg))

    def update(reg: Registration, all: Map[Period.Key,Return]) =
      f(V.update(reg, all))

    def update(reg: Registration, period: Period.Key, ret: Return) =
      f(V.update(reg, period, ret))
    
  }

}
