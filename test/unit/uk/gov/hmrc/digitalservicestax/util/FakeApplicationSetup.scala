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

package unit.uk.gov.hmrc.digitalservicestax.util

import org.apache.pekko.actor.ActorSystem
import org.scalatest.TryValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.{BaseOneAppPerSuite, FakeApplicationFactory, PlaySpec}
import play.api.i18n.MessagesApi
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.mvc.MessagesControllerComponents
import play.api.{Application, Configuration, Environment}
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.services.MongoPersistence
import uk.gov.hmrc.digitalservicestax.test.TestConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import java.io.File
import java.time.Clock
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait FakeApplicationSetup
    extends PlaySpec
    with BaseOneAppPerSuite
    with FakeApplicationFactory
    with TryValues
    with ScalaFutures
    with CleanMongoCollectionSupport {

  implicit lazy val actorSystem: ActorSystem   = app.actorSystem
  implicit lazy val ec: ExecutionContext       = scala.concurrent.ExecutionContext.Implicits.global
  implicit val hc: HeaderCarrier               = HeaderCarrier()
  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = 10.seconds, interval = 100.millis)
  implicit val clock: Clock                    = Clock.systemDefaultZone()
  implicit val c                               = this.mongoComponent

  lazy val appConfig: AppConfig              = app.injector.instanceOf[AppConfig]
  lazy val environment: Environment          = Environment.simple(new File("."))
  lazy val configuration: Configuration      = Configuration.load(environment)
  lazy val messagesApi: MessagesApi          = app.injector.instanceOf[MessagesApi]
  lazy val wsClient: WSClient                = app.injector.instanceOf[WSClient]
  lazy val httpAuditing: HttpAuditing        = app.injector.instanceOf[HttpAuditing]
  lazy val httpClient: HttpClient            = new DefaultHttpClient(configuration, httpAuditing, wsClient, actorSystem)
  lazy val mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]

  val mongoPersistence: MongoPersistence = app.injector.instanceOf[MongoPersistence]
  val servicesConfig: ServicesConfig     = app.injector.instanceOf[ServicesConfig]
  val testConnector: TestConnector       = new TestConnector(httpClient, appConfig)
  val appName: String                    = configuration.get[String]("appName")

  override def fakeApplication(): Application =
    GuiceApplicationBuilder(environment = environment)
      .configure(Map("tax-enrolments.enabled" -> "true", "auditing.enabled" -> "false"))
      .build()

}
