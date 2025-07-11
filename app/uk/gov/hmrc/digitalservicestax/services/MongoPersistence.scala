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
import org.mongodb.scala.model._
import play.api.libs.json._
import uk.gov.hmrc.digitalservicestax.data.BackendAndFrontendJson._
import uk.gov.hmrc.digitalservicestax.data._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.LocalDateTime
import javax.inject._
import scala.concurrent._

object MongoPersistence {

  case class CallbackWrapper(
    internalId: InternalId,
    formBundle: FormBundleNumber
  )

  case class RegWrapper(
    session: InternalId,
    data: Registration
  )

  case class RetWrapper(
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

    override def repository(): PlayMongoRepository[CallbackWrapper] = repo
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

    def repository(): PlayMongoRepository[RegWrapper] = repo

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

    override def findByRegistrationNumber(registrationNumber: DSTRegNumber): Future[Option[Registration]] =
      repo.collection
        .find(Filters.equal("data.registrationNumber", registrationNumber))
        .map(_.data)
        .headOption()

    override def findWrapperByRegistrationNumber(registrationNumber: DSTRegNumber): Future[Option[RegWrapper]] =
      repo.collection
        .find(Filters.equal("data.registrationNumber", registrationNumber))
        .headOption()

    override def findBySafeId(safeId: SafeId): Future[Option[RegWrapper]] =
      repo.collection
        .find(Filters.equal("data.companyReg.safeId", safeId))
        .headOption()

    override def findByEmail(email: Email): Future[Option[RegWrapper]] =
      repo.collection
        .find(Filters.equal("data.contact.email", email))
        .headOption()

    override def delete(registrationNumber: DSTRegNumber): Future[Long] =
      repo.collection
        .deleteOne(Filters.equal("data.registrationNumber", registrationNumber))
        .toFuture()
        .map(_.getDeletedCount)
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

    override def repository(): PlayMongoRepository[RetWrapper] = repo

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
