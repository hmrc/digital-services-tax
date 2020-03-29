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


import java.io.File
import java.time.Clock

import com.softwaremill.macwire._
import play.api.{Configuration, Environment}
import play.modules.reactivemongo.ReactiveMongoApi
import uk.gov.hmrc.digitalservicestax.config.AppConfig
import uk.gov.hmrc.digitalservicestax.services.MongoPersistence
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.config.AuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.audit.DefaultAuditConnector
import uk.gov.hmrc.play.bootstrap.config.{AuditingConfigProvider, RunMode, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpAuditing

trait TestWiring {
  val appName: String = configuration.get[String]("appName")

  lazy val auditingConfigProvider: AuditingConfigProvider = wire[AuditingConfigProvider]

  val auditingconfig: AuditingConfig = auditingConfigProvider.get()

  lazy val auditConnector: AuditConnector = wire[DefaultAuditConnector]
  lazy val httpAuditing: HttpAuditing = wire[DefaultHttpAuditing]
  lazy val configuration: Configuration = Configuration.load(environment, Map("auditing.enabled" -> "false"))
  lazy val runMode: RunMode = wire[RunMode]
  lazy val environment: Environment = Environment.simple(new File("."))
  lazy val mode = environment.mode

  implicit def clock: Clock = Clock.systemDefaultZone()
  lazy val appConfig: AppConfig = wire[AppConfig]
  val servicesConfig: ServicesConfig = wire[ServicesConfig]
  val reactiveMongoApi: ReactiveMongoApi = wire[ReactiveMongoApi]
  val mongoPersistence: MongoPersistence = wire[MongoPersistence]

}
