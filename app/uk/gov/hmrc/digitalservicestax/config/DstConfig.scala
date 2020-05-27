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

package uk.gov.hmrc.digitalservicestax.config

import java.time.LocalDate

trait WithUrlConfig {
  def port: Int
  def host: String
  def protocol: String
  def baseUrl: String = s"$protocol://$host:$port"
}

case class ServiceConfig(
  port: Int,
  host: String,
  protocol: String = "http",
  enabled: Boolean = true
) extends WithUrlConfig

case class TaxEnrolmentsConfig (
  port: Int,
  host: String,
  callback: String,
  enabled: Boolean = true,
  serviceName: String,
  protocol: String = "http",
) extends WithUrlConfig

case class DesConfig (
  port: Int,
  host: String,
  protocol: String = "http",
  environment: String = "",
  token: String = ""
) extends WithUrlConfig

case class HttpConfig(
  router: Option[String] = None
)

case class PlayConfig(
  http: HttpConfig = HttpConfig()
)

case class LoggingConfig (
  registerResponse: Boolean = false
)

case class UpstreamServicesConfig(
  graphite: ServiceConfig,
  auth: ServiceConfig,
  contactFrontend: ServiceConfig,
  des: DesConfig,
  email: ServiceConfig,
  taxEnrolments: TaxEnrolmentsConfig
)

case class DstConfig(
  appName: String,
  play: PlayConfig = PlayConfig(),
  upstreamServices: UpstreamServicesConfig,
  // auth: ServiceConfig,
  // contactFrontend: ServiceConfig,
  // des: ServiceConfig,
  // taxEnrolments: ServiceConfig, 
  // auditing: AuditConfig,
  // graphiteHost: String,
  logging: LoggingConfig,
  obligationStartDate: LocalDate = LocalDate.of(2020,4,1)
)
