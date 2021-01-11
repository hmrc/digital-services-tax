/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.digitalservicestax.util


import akka.actor.ActorSystem
import com.softwaremill.macwire.wire
import org.scalatest.TryValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.{BaseOneAppPerSuite, FakeApplicationFactory, PlaySpec}
import play.api.i18n.MessagesApi
import play.api.inject.DefaultApplicationLifecycle
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.mvc.MessagesControllerComponents
import play.api.{Application, ApplicationLoader}
import play.core.DefaultWebCommands
import play.modules.reactivemongo.DefaultReactiveMongoApi
import reactivemongo.api.MongoConnection
import uk.gov.hmrc.digitalservicestax.services.MongoPersistence
import uk.gov.hmrc.digitalservicestax.test.TestConnector
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpClient, HttpClient}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

trait FakeApplicationSpec extends PlaySpec
  with BaseOneAppPerSuite
  with MongoSpecSupport
  with FakeApplicationFactory
  with TryValues
  with ScalaFutures
  with TestWiring {
  protected[this] val context: ApplicationLoader.Context = ApplicationLoader.Context(
    environment,
    sourceMapper = None,
    new DefaultWebCommands,
    configuration,
    new DefaultApplicationLifecycle
  )

  implicit lazy val actorSystem: ActorSystem = app.actorSystem

  lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
  lazy val httpClient: HttpClient = new DefaultHttpClient(configuration, httpAuditing, wsClient, actorSystem)
  lazy val mcc = app.injector.instanceOf[MessagesControllerComponents]

  val testConnector: TestConnector = new TestConnector(httpClient, environment, configuration, servicesConfig)

  override def fakeApplication(): Application = {
    GuiceApplicationBuilder(environment = environment).configure(
      Map(
        "tax-enrolments.enabled" -> "true"
      )
    ).build()
  }

  val reactiveMongoApi = new DefaultReactiveMongoApi(
    parsedUri = MongoConnection.parseURI(mongoUri).success.value,
    dbName = databaseName,
    strictMode = false,
    configuration = configuration,
    new DefaultApplicationLifecycle
  )

  val mongoPersistence: MongoPersistence = wire[MongoPersistence]

  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = 10.seconds, interval = 100.millis)
}