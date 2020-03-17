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

package uk.gov.hmrc.digitalservicestax.util


import akka.actor.ActorSystem
import org.scalatestplus.play.{BaseOneAppPerSuite, FakeApplicationFactory, PlaySpec}
import play.api.i18n.MessagesApi
import play.api.inject.DefaultApplicationLifecycle
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.{Application, ApplicationLoader}
import play.core.DefaultWebCommands
import uk.gov.hmrc.digitalservicestax.test.TestConnector
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpClient, HttpClient}

trait FakeApplicationSpec extends PlaySpec
  with BaseOneAppPerSuite
  with FakeApplicationFactory
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

  val testConnector: TestConnector = new TestConnector(httpClient, environment, configuration, servicesConfig)

  override def fakeApplication(): Application = {
    GuiceApplicationBuilder(environment = environment).build()
  }
}
