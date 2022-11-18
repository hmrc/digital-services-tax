/*
 * Copyright 2022 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class AppConfig @Inject() (config: Configuration, servicesConfig: ServicesConfig) {

  val desURL: String         = servicesConfig.baseUrl("des")
  val desEnvironment: String = servicesConfig.getConfString("des.environment", "")
  val desToken: String       = servicesConfig.getConfString("des.token", "")

  val authBaseUrl: String = servicesConfig.baseUrl("auth")

  val emailUrl: String = servicesConfig.baseUrl("email")

  val auditingEnabled: Boolean = config.get[Boolean]("auditing.enabled")
  val graphiteHost: String     = config.get[String]("microservice.metrics.graphite.host")

  val obligationStartDate: String = config.getOptional[String]("obligation-data.fromDate").getOrElse("2020-04-01")

  val fixFailedCallback: Boolean = config.getOptional[Boolean]("fix.callback-failure.enabled").getOrElse(false)

  val taxEnrolmentsCallbackUrl: String = servicesConfig.getConfString("tax-enrolments.callback", "")
  val taxEnrolmentsServiceName: String = servicesConfig.getConfString("tax-enrolments.serviceName", "")
  val taxEnrolmentsEnabled: Boolean    = servicesConfig.getConfBool("tax-enrolments.enabled", true)
  val taxEnrolmentsUrl: String         = servicesConfig.baseUrl("tax-enrolments")
}
