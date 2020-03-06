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
import cats.instances.future._

import reactivemongo.api.Cursor
import reactivemongo.play.json._, collection._
import play.modules.reactivemongo._
import javax.inject._

object MongoPersistence {

  private[services] case class CallbackWrapper(
    internalId: String, 
    formBundle: String,
    timestamp: LocalDateTime = LocalDateTime.now
  )

  private[services] case class RegWrapper(
    session: String, 
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
  implicit val formatWrapper = Json.format[CallbackWrapper]

  val pendingCallbacks = new PendingCallbacks {
    lazy val collection: Future[JSONCollection] = {
      database.map(_.collection[JSONCollection]("pending-callbacks")).flatMap { c =>

        val sessionIndex = Index(
          key = Seq("formBundle" -> IndexType.Ascending),
          unique = true
        )
        
        c.indexesManager.ensure(sessionIndex).map { case _ => c }
      }
    }

    def get(formBundle: String) =  {
      val selector = Json.obj("formBundle" -> formBundle)
      collection.flatMap(
        _.find(selector)
          .one[CallbackWrapper]
      ).map{_.map{_.internalId}}
    }

    def delete(formBundle: String) =  {
      val selector = Json.obj("formBundle" -> formBundle)
      collection.flatMap(_.remove(selector)).map{_ => ()}
    }

    def update(formBundle: String, internalId: String) = {
      val wrapper = CallbackWrapper(internalId, formBundle)
      val selector = Json.obj("formBundle" -> formBundle)      
      collection.flatMap(_.update(ordered = false).one(selector, wrapper, upsert = true)).map{
          case wr: reactivemongo.api.commands.WriteResult if wr.writeErrors.isEmpty => ()
          case e => throw new Exception(s"$e")
        }
    }
    
  }

  val registrations = new Registrations {

    lazy val collection: Future[JSONCollection] = {
      database.map(_.collection[JSONCollection]("registrations")).flatMap { c =>

        val sessionIndex = Index(
          key = Seq("session" -> IndexType.Ascending),
          unique = true
        )
        
        c.indexesManager.ensure(sessionIndex).map { case _ => c }
      }
    }

    implicit val formatWrapper = Json.format[RegWrapper]

    override def update(user: String, value: Registration): Future[Unit] = {
      val wrapper = RegWrapper(user, value)
      val selector = Json.obj("session" -> user)      
      collection.flatMap(_.update(ordered = false).one(selector, wrapper, upsert = true)).map{
          case wr: reactivemongo.api.commands.WriteResult if wr.writeErrors.isEmpty => ()
          case e => throw new Exception(s"$e")
        }
    }

    override def get(user: String): Future[Option[Registration]] = {
      val selector = Json.obj("session" -> user)
      collection.flatMap(_.find(selector).one[RegWrapper]).map{_.map{_.data}}
    }

    def getByFormBundleNumber(formBundleNumber: FormBundleNumber): Future[Option[Registration]] = {
//      val selector = Json.obj("session" -> user)
//      collection.flatMap(_.find(selector).one[RegWrapper]).map{_.map{_.data}}
      ???
    }


  }

  def returns = new Returns {

    lazy val collection: Future[JSONCollection] = {
      database.map(_.collection[JSONCollection]("returns")).flatMap { c =>
        val sessionIndex = Index(
          key = Seq(
            "regNo" -> IndexType.Ascending,
            "periodKey" -> IndexType.Ascending
          ),
          unique = true
        )
        
        c.indexesManager.ensure(sessionIndex).map { case _ => c }
      }
    }

    implicit val formatWrapper = Json.format[RetWrapper]

    def get(reg: Registration): Future[Map[Period.Key, Return]] =  {
      val selector = Json.obj("regNo" -> reg.registrationNumber.fold(
        throw new IllegalArgumentException("Registration is not active"))
      (_.toString))
      collection.flatMap(
        _.find(selector)
          .cursor[RetWrapper]()
          .collect[List](
            maxDocs = 1000,
            err = Cursor.FailOnError[List[RetWrapper]]()
          ).map { _.map{x => (x.periodKey, x.data)}.toMap }
      )
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
  }

}
