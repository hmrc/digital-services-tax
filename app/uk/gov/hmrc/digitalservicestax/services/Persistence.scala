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

import uk.gov.hmrc.mongo.{MongoConnector, ReactiveRepository}
import scala.concurrent._
import java.time.LocalDateTime
import data._, BackendAndFrontendJson._
import reactivemongo.api.indexes.{Index, IndexType}
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import cats.syntax.functor._
import cats.instances.future._

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

class MongoPersistence(mc: MongoConnector)(implicit ec: ExecutionContext) extends Persistence[Future] {

  val registrations = new Registrations {
    protected val mongo =
      new ReactiveRepository[Wrapper, BSONObjectID]("sdilsubscriptions", mc.db, formatWrapper, implicitly) {
        override def indexes: Seq[Index] = Seq(
          Index(
            key = Seq(
              "user" -> IndexType.Ascending
            ),
            unique = true
          )
        )
      }

    protected case class Wrapper(
      user: String,
      registration: Registration,
      retrievalTime: LocalDateTime = LocalDateTime.now(),
      _id: Option[BSONObjectID] = None
    )

    implicit val formatWrapper = Json.format[Wrapper]

    override def update(user: String, value: Registration): Future[Unit] =
      mongo.insert(Wrapper(user, value)).map(_ => ())

    override def get(user: String): Future[Option[Registration]] =
      mongo
        .find(
          "user" -> user
        )
        .map(_.map(_.registration).headOption)

    def confirm(user: String, newRegNo: DSTRegNumber): Future[Unit] =
      apply(user).flatMap{old =>
        update(user, old.copy(registrationNumber = Some(newRegNo)))
      }

    def dropDb = mongo.drop
  }

  def returns = new Returns {
    def get(reg: Registration): Future[Map[Period, Return]] = ???
    def update(reg: Registration, period: Period, ret: Return): Future[Unit] = ??? 
    def update(reg: Registration, all: Map[Period,Return]): Future[Unit] = ???
  }
}
