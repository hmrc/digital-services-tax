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

package ltbs.resilientcalls

import cats.implicits._
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import play.api.Logger
import akka.actor.ActorSystem
import play.api.libs.json._
import play.modules.reactivemongo._
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.play.json._
import collection._

import scala.concurrent._
import javax.inject._
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration.FiniteDuration

@Singleton
class DstMongoProvider @Inject()(
  mongo: ReactiveMongoApi,
  val actorSystem: ActorSystem,
  val appConfig: AppConfig,
  implicit val ec: ExecutionContext
) extends MongoProvider[(Int, String)](mongo,
  {
    case uk.gov.hmrc.http.Upstream4xxResponse(msg, code, _, _) => (code, msg)
    case uk.gov.hmrc.http.Upstream5xxResponse(msg, code, _) => (code, msg)
    case e => (-1, e.getLocalizedMessage)
  }
) with ActorTrigger {

  override def triggerAction: Future[Unit] = tick()

}

trait ActorTrigger {
  val actorSystem: ActorSystem
  val appConfig: AppConfig
  implicit val ec: ExecutionContext

  def triggerAction: Future[Unit]

  def trigger = actorSystem.scheduler.schedule(
    FiniteDuration(10, TimeUnit.SECONDS),
    new FiniteDuration(appConfig.resilienceTickInterval, TimeUnit.SECONDS)
  ) {
    triggerAction
  }
}


class MongoProvider[E](
  mongo: ReactiveMongoApi,
  readError: Throwable => E
)(implicit ec: ExecutionContext, errFmt: Format[E]) extends ResilienceProvider[Future, Format, Format, E] {

  val maxTasks: Int = 1

  val log = Logger("resilience")

  @volatile private var functions: List[() => Future[Unit]] = Nil

  def tick(): Future[Unit] =
    functions.map{_.apply}.sequence_

  class MongoFuncImpl[I: Format, O: Format](
    val key: String,
    val inner: I => Future[O],
    val rule: RetryRule[E]
  ) extends ResilientFunction[Future, I, O, E] {

    import mongo.database
    lazy val collection: Future[JSONCollection] =
      database.map(_.collection[JSONCollection](s"resilience-$key")).flatMap {
        c =>
        import reactivemongo.api.indexes.{Index, IndexType}
        val uuidIndex = Index(
          key = Seq("uuid" -> IndexType.Ascending),
          unique = true
        )

        c.indexesManager.ensure(uuidIndex).map{_ => c}
      }

    case class Wrapper(
      uuid: ID,
      input: I,
      attempts: List[(LocalDateTime, E)],
      nextAttempt: Option[LocalDateTime] = Some(LocalDateTime.now),
      output: Option[O] = None
    )

    implicit def wrapperFormat: OFormat[Wrapper] = {

      implicit def aFormat = new Format[ID] {
        def reads(json: JsValue): JsResult[ID] = json match {
          case JsString(s) =>
            Either.catchOnly[IllegalArgumentException](UUID.fromString(s)) match {
              case Left(_) => JsError("Invalid UUID")
              case Right(id) => JsSuccess(shapeless.tag[IDTag][UUID](id))
            }
          case wrongType => JsError(s"found $wrongType, expecting JsString")
        }
        def writes(o: ID): JsValue = JsString(o.toString)
      }

      Json.format[Wrapper]
    }

    def async(input: I): Future[ID] = {
      val newId = randomID()
      val record = Wrapper(newId, input, Nil)
      log.info(s"$key / $newId: task created")
      collection.flatMap(_.insert(ordered = false).one(record)).map{_ => newId}
    }

    def get(id: ID): Future[Either[List[(LocalDateTime, E)],O]] = {
      val selector = Json.obj("uuid" -> id.toString)
      val r = collection.flatMap(_.find(selector).one[Wrapper])
      r.map{
        case Some(Wrapper(_, _, _, _, Some(o))) => Right(o)
        case Some(Wrapper(_, _, att, _, None)) => Left(att)          
        case None => Left(List.empty[(LocalDateTime, E)])
      }
    }

    def tick: Future[Unit] = {
      val selector = Json.obj(
        "nextAttempt" -> Json.obj("$lt" -> LocalDateTime.now)
      )

      collection.flatMap{c =>
        val tasks = c.find(selector)
          .cursor[Wrapper](ReadPreference.primary)
          .collect[List](maxTasks, Cursor.FailOnError[List[Wrapper]]())

        tasks.flatMap{ allWrappers =>
          val allTasks = allWrappers.collect{
            case Wrapper(uuid, i, att, _, None) =>
              val result = inner(i).map(_.asRight).recover{case e => e.asLeft[O]}
              result flatMap { r =>
                val newRecord = r match {
                  case Left(e) =>
                    val newAtt = (LocalDateTime.now, readError(e)) :: att
                    val nextRerun = rule.nextRetry(newAtt);
                    {
                      val basicLogMessage =
                        s"$key / $uuid / attempt #${att.size + 1}: ${e.getLocalizedMessage}"
                      nextRerun match {
                        case Some(when) =>
                          log.warn(s"${basicLogMessage}\n  Next attempting after $when.")
                        case None =>
                          log.error(
                            List(
                              basicLogMessage,
                              "  Giving up - no further attempts.",
                              s"  original payload: $i"
                            ).mkString("\n")
                          )
                      }
                    }
                    Wrapper(uuid, i, newAtt, nextRerun, None)
                  case Right(o) =>
                    log.info(s"$key / $uuid / attempt #${att.size + 1}: $o")
                    Wrapper(uuid, i, att, None, Some(o))
                }
                val selector = Json.obj("uuid" -> uuid.toString)
                collection.flatMap(_.update(selector, newRecord))
              }
          }
          allTasks.sequence_
        }
      }
    }
  }

  def apply[I: Format, O: Format](
    key: String,
    f: I => Future[O],
    rule: RetryRule[E]
  ): ResilientFunction[Future, I, O, E] = {
    val out = new MongoFuncImpl[I,O](key, f, rule)
    functions = out.tick _ :: functions
    out
  }
}
