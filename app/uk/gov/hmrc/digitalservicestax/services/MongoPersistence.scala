/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.instances.future._
import data.BackendAndFrontendJson._
import data._
import java.time.LocalDateTime
import javax.inject._
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
import play.api.libs.json._
import scala.concurrent._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import org.mongodb.scala.model.Updates
import org.mongodb.scala.model.FindOneAndUpdateOptions
import uk.gov.hmrc.mongo.play.json.Codecs

object MongoPersistence {

  private[services] case class EnrolmentWrapper(
    internalId: InternalId,
    safeId: SafeId,
    formBundle: FormBundleNumber
  )

  private[services] case class CallbackWrapper(
    internalId: InternalId,
    formBundle: FormBundleNumber
  )

  private[services] case class RegWrapper(
    session: InternalId,
    data: Registration
  )

  private[services] case class RetWrapper(
    regNo: DSTRegNumber,
    periodKey: Period.Key,
    data: Return
  )
}

@Singleton
class MongoPersistence @Inject() (
  mongoc: MongoComponent
)(implicit ec: ExecutionContext)
    extends Persistence[Future] {
  import MongoPersistence._

  val pendingCallbacks: PendingCallbacks = new PendingCallbacks {

    lazy val repo = new PlayMongoRepository[CallbackWrapper](
      collectionName = "pending-callbacks",
      mongoComponent = mongoc,
      domainFormat = Json.format[CallbackWrapper],
      indexes = Seq(
        IndexModel(
          Indexes.ascending("internalId"),
          IndexOptions().unique(true)
        ),
        IndexModel(
          Indexes.ascending("formBundle"),
          IndexOptions().unique(true)
        )
      ),
      extraCodecs = Nil
    )

    def get(formBundle: FormBundleNumber): Future[Option[InternalId]] =
      repo.collection
        .find(Filters.equal("formBundle", formBundle))
        .map(_.internalId)
        .headOption()

    def reverseLookup(internalId: InternalId): Future[Option[FormBundleNumber]] =
      repo.collection
        .find(Filters.equal("internalId", internalId))
        .map(_.formBundle)
        .headOption()

    def delete(formBundle: FormBundleNumber): Future[Unit] =
      repo.collection
        .deleteOne(Filters.equal("formBundle", formBundle))
        .toFuture()
        .map(_ => ())

    def update(formBundle: FormBundleNumber, internalId: InternalId): Future[Unit] = {
      val wrapper = CallbackWrapper(internalId, formBundle)
      repo.collection
        .findOneAndUpdate(
          Filters.equal("formBundle", formBundle),
          Updates.combine(
            Updates.setOnInsert("formBundle", formBundle),
            Updates.set("internalId", wrapper.internalId),
            Updates.setOnInsert("created", LocalDateTime.now),
            Updates.set("updated", LocalDateTime.now)
          ),
          FindOneAndUpdateOptions().upsert(true)
        )
        .toFuture()
        .map(_ => ())
    }

  }

  val registrations: Registrations = new Registrations {

    lazy val repo = new PlayMongoRepository[RegWrapper](
      collectionName = "registrations",
      mongoComponent = mongoc,
      domainFormat = Json.format[RegWrapper],
      indexes = Seq(
        IndexModel(
          Indexes.ascending("session"),
          IndexOptions().unique(true)
        ),
        IndexModel(
          Indexes.ascending("data.registrationNumber"),
          IndexOptions().unique(false)
        )
      ),
      extraCodecs = Nil
    )

    def update(user: InternalId, value: Registration): Future[Unit] =
      repo.collection
        .findOneAndUpdate(
          Filters.equal("session", user),
          Updates.combine(
            Updates.setOnInsert("session", user),
            Updates.set("data", Codecs.toBson(value)),
            Updates.setOnInsert("created", LocalDateTime.now),
            Updates.set("updated", LocalDateTime.now)
          ),
          FindOneAndUpdateOptions().upsert(true)
        )
        .toFuture()
        .map(_ => ())

    override def get(user: InternalId): Future[Option[Registration]] =
      repo.collection
        .find(Filters.equal("session", user))
        .map(_.data)
        .headOption()

    override def getByRegistrationNumber(registrationNumber: DSTRegNumber): Future[Option[Registration]] = {
      repo.collection
        .find(Filters.equal("data.registrationNumber", registrationNumber))
        .map(_.data)
        .headOption()
    }
  }

  def returns = new Returns {

    lazy val repo = new PlayMongoRepository[RetWrapper](
      collectionName = "returns",
      mongoComponent = mongoc,
      domainFormat = Json.format[RetWrapper],
      indexes = Seq(
        IndexModel(
          Indexes.compoundIndex(
            Indexes.ascending("regNo"),
            Indexes.ascending("periodKey")
          ),
          IndexOptions().unique(true)
        )
      ),
      extraCodecs = Nil
    )

    def get(reg: Registration): Future[Map[Period.Key, Return]] =
      reg.registrationNumber match {
        case Some(regNo) =>
          repo.collection
            .find(Filters.equal("regNo", regNo))
            .limit(1000)
            .toFuture()
            .map(_.map(x => (x.periodKey, x.data)).toMap)
        case None        =>
          Future.failed(new IllegalArgumentException("Registration is not active"))
      }

    def update(reg: Registration, period: Period.Key, ret: Return): Future[Unit] = {

      val regNo = reg.registrationNumber.getOrElse(
        throw new IllegalArgumentException("Registration is not active")
      )

      repo.collection
        .findOneAndUpdate(
          Filters.and(
            Filters.equal("regNo", regNo),
            Filters.equal("periodKey", period)
          ),
          Updates.combine(
            Updates.setOnInsert("regNo", regNo),
            Updates.setOnInsert("periodKey", period),
            Updates.set("data", Codecs.toBson(ret)),
            Updates.setOnInsert("created", LocalDateTime.now),
            Updates.set("updated", LocalDateTime.now)
          ),
          FindOneAndUpdateOptions().upsert(true)
        )
        .toFuture()
        .map(_ => ())
    }
  }
}
