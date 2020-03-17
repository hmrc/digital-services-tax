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


import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.{BaseOneAppPerSuite, FakeApplicationFactory, PlaySpec}
import play.api.i18n.MessagesApi
import play.api.inject.DefaultApplicationLifecycle
import play.api.libs.ws.WSClient
import play.api.{Application, ApplicationLoader}
import play.core.DefaultWebCommands
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpClient, HttpClient}

import scala.collection.mutable
import scala.concurrent.{Future, ExecutionContext => EC}

trait FakeApplicationSpec
  extends PlaySpec with BaseOneAppPerSuite with FakeApplicationFactory with TestWiring with MockitoSugar {
  protected val context = ApplicationLoader.Context(
    environment,
    sourceMapper = None,
    new DefaultWebCommands,
    configuration,
    new DefaultApplicationLifecycle
  )

  lazy val messagesApi = app.injector.instanceOf[MessagesApi]
  lazy val wsClient = app.injector.instanceOf[WSClient]
  lazy val httpClient: HttpClient = new DefaultHttpClient(configuration, httpAuditing, wsClient, actorSystem)

  override def fakeApplication(): Application =
    new FakeApplicationFactory().load(context)

}
