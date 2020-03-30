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

import data._
import BackendAndFrontendJson._
import reactivemongo.api.indexes.{Index, IndexType}
import play.api.libs.json._
import cats.instances.future._
import reactivemongo.api.{Cursor, WriteConcern}
import reactivemongo.play.json._
import collection._
import play.modules.reactivemongo._
import javax.inject._
import uk.gov.hmrc.digitalservicestax.data.Period.Key

object MongoPersistence {

  private[services] case class CallbackWrapper(
    internalId: InternalId, 
    formBundle: FormBundleNumber,
    timestamp: LocalDateTime = LocalDateTime.now
  )

  private[services] case class RegWrapper(
    session: InternalId, 
    data: Registration,
    timestamp: LocalDateTime = LocalDateTime.now
  )

  private[services] case class RetWrapper(
    regNo: DSTRegNumber, 
    periodKey: Period.Key,
    data: Return,
    timestamp: LocalDateTime = LocalDateTime.now
  )

}

@Singleton
class MongoPersistence @Inject()(
  mongo: ReactiveMongoApi
)(implicit ec: ExecutionContext) extends Persistence[Future] {
  import mongo.database
  import MongoPersistence._
  implicit val formatWrapper: OFormat[CallbackWrapper] = Json.format[CallbackWrapper]

  val pendingCallbacks: PendingCallbacks = new PendingCallbacks {
    lazy val collection: Future[JSONCollection] = {
      database.map(_.collection[JSONCollection]("pending-callbacks")).flatMap { c =>

        val sessionIndex = Index(
          key = Seq("formBundle" -> IndexType.Ascending),
          unique = true
        )
        
        c.indexesManager.ensure(sessionIndex).map(_ => c)
      }
    }

    def insert(formBundleNumber: FormBundleNumber, internalId: InternalId): Future[Unit] = {
      val wrapper = CallbackWrapper(internalId, formBundleNumber)
      collection.flatMap(_.insert(ordered = true, WriteConcern.Journaled).one(wrapper)).map {
        case wr: reactivemongo.api.commands.WriteResult if wr.writeErrors.isEmpty => ()
        case e => throw new Exception(s"$e")
      }
    }

    def get(formBundle: FormBundleNumber): Future[Option[InternalId]] =  {
      val selector = Json.obj("formBundle" -> formBundle.toString)
      collection.flatMap(
        _.find(selector)
          .one[CallbackWrapper]
      ).map{_.map{_.internalId}}
    }

    def delete(formBundle: FormBundleNumber): Future[Unit] =  {
      val selector = Json.obj("formBundle" -> formBundle.toString)
      collection.flatMap(_.delete.one(selector)).map{_ => ()}
    }

    def update(formBundle: FormBundleNumber, internalId: InternalId): Future[Unit] = {
      val wrapper = CallbackWrapper(internalId, formBundle)
      val selector = Json.obj("formBundle" -> formBundle.toString)      
      collection.flatMap(_.update(ordered = false).one(selector, wrapper, upsert = true)).map{
          case wr: reactivemongo.api.commands.WriteResult if wr.writeErrors.isEmpty => ()
          case e => throw new Exception(s"$e")
        }
    }
    
  }

  val registrations: Registrations = new Registrations {

    lazy val collection: Future[JSONCollection] = {
      database.map(_.collection[JSONCollection]("registrations")).flatMap { c =>

        val sessionIndex = Index(
          key = Seq("session" -> IndexType.Ascending),
          unique = true
        )
        
        c.indexesManager.ensure(sessionIndex).map(_ => c)
      }
    }

    implicit val formatWrapper: OFormat[RegWrapper] = Json.format[RegWrapper]

    def insert(user: InternalId, value: Registration): Future[Unit] = {
      val wrapper = RegWrapper(user, value)
      collection.flatMap(_.insert(ordered = false, WriteConcern.Journaled).one(wrapper)).map {
        case wr: reactivemongo.api.commands.WriteResult if wr.writeErrors.isEmpty => ()
        case e => throw new Exception(s"$e")
      }
    }

    override def update(user: InternalId, value: Registration): Future[Unit] = {
      val wrapper = RegWrapper(user, value)
      val selector = Json.obj("session" -> user.toString)
      collection.flatMap(_.update(ordered = false).one(selector, wrapper, upsert = true)).map{
          case wr: reactivemongo.api.commands.WriteResult if wr.writeErrors.isEmpty => ()
          case e => throw new Exception(s"$e")
        }
    }

    override def get(user: InternalId): Future[Option[Registration]] = {
      val selector = Json.obj("session" -> user.toString)
      collection.flatMap(_.find(selector).one[RegWrapper]).map{_.map{_.data}}
    }

  }

  def returns: Returns = new Returns {

    lazy val collection: Future[JSONCollection] = {
      database.map(_.collection[JSONCollection]("returns")).flatMap { c =>
        val sessionIndex = Index(
          key = Seq(
            "regNo" -> IndexType.Ascending,
            "periodKey" -> IndexType.Ascending
          ),
          unique = true
        )
        
        c.indexesManager.ensure(sessionIndex).map(_ => c)
      }
    }

    implicit val formatWrapper: OFormat[RetWrapper] = Json.format[RetWrapper]

    def get(reg: Registration): Future[Map[Period.Key, Return]] =  {
      reg.registrationNumber match {
        case Some(regNo) =>
          val selector = Json.obj("regNo" -> regNo.toString)

          collection.flatMap(
            _.find(selector)
              .cursor[RetWrapper]()
              .collect[List](
                maxDocs = 1000,
                err = Cursor.FailOnError[List[RetWrapper]]()
              ).map { _.map{x => (x.periodKey, x.data)}.toMap }
          )

        case None => Future.failed(new IllegalArgumentException("Registration is not active"))
      }

    }

    def update(reg: Registration, period: Period.Key, ret: Return): Future[Unit] = {
      val selector = Json.obj(
        "regNo" -> reg.registrationNumber.fold(
          throw new IllegalArgumentException("Registration is not active"))
          (_.toString), 
        "periodKey" -> period.toString
      )

      val wrapper = RetWrapper(
        reg.registrationNumber.getOrElse(
          throw new IllegalArgumentException("Registration is not active")
        ),
        period,
        ret
      )

      collection.flatMap(
        _.update(ordered = false)
          .one(selector, wrapper, upsert = true)
      ).map{
        case wr: reactivemongo.api.commands.WriteResult if wr.writeErrors.isEmpty => ()
        case e => throw new Exception(s"$e")
      }
    }

    override def insert(reg: Registration, key: Key, ret: Return): Future[Unit] = {
      val wrapper = RetWrapper(reg.registrationNumber.get, key, ret)

      collection.flatMap(_.insert(ordered = false, WriteConcern.Journaled).one(wrapper)).map {
        case wr: reactivemongo.api.commands.WriteResult if wr.writeErrors.isEmpty => ()
        case e => throw new Exception(s"$e")
      }
    }
  }

}
