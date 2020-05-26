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

package uk.gov.hmrc.digitalservicestax

import _root_.controllers.AssetsComponents
import com.kenshoo.play.metrics.MetricsController
import com.kenshoo.play.metrics.{Metrics, MetricsImpl}
import com.softwaremill.macwire._
import play.api._, ApplicationLoader.Context
import play.api.i18n._
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.BodyParsers
import play.api.mvc.{MessagesActionBuilder, DefaultMessagesActionBuilderImpl, DefaultMessagesControllerComponents}
import play.api.routing.Router
import play.modules.reactivemongo.ReactiveMongoApiFromContext
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.bootstrap.audit.DefaultAuditConnector
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.config._
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpAuditing, DefaultHttpClient, HttpClient}
import uk.gov.hmrc.play.health.HealthController
import play.api.mvc.EssentialFilter
import uk.gov.hmrc.play.bootstrap.filters.MDCFilter
import uk.gov.hmrc.play.bootstrap.filters.microservice.DefaultMicroserviceAuditFilter
import uk.gov.hmrc.play.bootstrap.filters.CacheControlFilter
import com.kenshoo.play.metrics.MetricsFilterImpl
import uk.gov.hmrc.play.bootstrap.filters.CacheControlConfig
import uk.gov.hmrc.play.bootstrap.filters.DefaultLoggingFilter

/**
 * Application loader that wires up the application dependencies using Macwire
 */
class DstApplicationLoader extends ApplicationLoader {
  def load(context: Context): Application =
    new DstComponents(context).application
}

object DefaultBase64ConfigDecoder extends Base64ConfigDecoder {
  override def decodeConfig(
    configuration: Configuration
  ): Configuration = super.decodeConfig(configuration)
}

abstract class BasicComponents(context: Context)
    extends ReactiveMongoApiFromContext(context)
    with AssetsComponents
    with I18nComponents
    with BuiltInComponents
    with AhcWSComponents
    with play.filters.HttpFiltersComponents
{
  // ----------------------------------------
  // basic play parts
  // ----------------------------------------
  LoggerConfigurator(context.environment.classLoader).foreach {
    _.configure(context.environment, context.initialConfiguration, Map.empty)
  }
  override implicit lazy val environment: Environment = context.environment
  def httpClient: HttpClient = wire[DefaultHttpClient]
  lazy val bp = new BodyParsers.Default(playBodyParsers)
  lazy val messagesactionbuilder: MessagesActionBuilder = wire[DefaultMessagesActionBuilderImpl]
  override lazy val controllerComponents = wire[DefaultMessagesControllerComponents]

  implicit override def configuration: Configuration = DefaultBase64ConfigDecoder.decodeConfig(
    context.initialConfiguration
  )

  // ----------------------------------------
  // assorted bits of hmrc 
  // ----------------------------------------

  val mode = environment.mode
  lazy val runMode = new RunMode(configuration, mode)
  val appName = configuration.get[String]("appName")
  lazy val auditingConfigProvider: AuditingConfigProvider =
    new AuditingConfigProvider(configuration, runMode, appName)
  val auditConnector = new DefaultAuditConnector(auditingConfigProvider.get())
  lazy val serviceConfig = new ServicesConfig(configuration, runMode)

  val auditing: HttpAuditing = new DefaultHttpAuditing(auditConnector, configuration.get[String]("appName"))
  lazy val authConnector: AuthConnector = wire[DefaultAuthConnector]
  lazy val metrics: Metrics = wire[MetricsImpl]
  lazy val metricsController = wire[MetricsController]
  lazy val healthController = wire[HealthController]

  // filters
  lazy val controllerConfigs: ControllerConfigs = wireWith(ControllerConfigs.fromConfig _)
  lazy val cacheControlConfig: CacheControlConfig = wireWith(CacheControlConfig.fromConfig _)
  lazy val httpAuditEvent: HttpAuditEvent = wire[DefaultHttpAuditEvent]

  override def httpFilters: Seq[EssentialFilter] = Seq(
    wire[MetricsFilterImpl],
    wire[DefaultMicroserviceAuditFilter],
    wire[DefaultLoggingFilter],
    wire[CacheControlFilter],
    wire[MDCFilter]
  )
  
}

class DstComponents(context: Context) extends BasicComponents(context) { 

  lazy val mongo = wire[services.MongoPersistence]
  lazy val loggedInAction = wire[actions.LoggedInAction]
  lazy val registeredAction = wire[actions.Registered]
  lazy val appConfig = new config.AppConfig(configuration, serviceConfig)

  // connectors
  lazy val retConnector = wire[connectors.ReturnConnector]
  lazy val regConnector = wire[connectors.RegistrationConnector]
  lazy val testConnector = wire[test.TestConnector]
  lazy val emailConnector = wire[connectors.EmailConnector]
  lazy val taxEnrolConnector = wire[connectors.TaxEnrolmentConnector]
  lazy val rosmConnector = wire[connectors.RosmConnector]

  // controllers
  implicit lazy val returnsController = wire[controllers.ReturnsController]
  lazy val regController = wire[controllers.RegistrationsController]
  lazy val testController = wire[test.TestController]
  lazy val rosmController = wire[controllers.RosmController]
  lazy val teC = wire[controllers.TaxEnrolmentCallbackController]

  // routing
  def router: Router = {

    lazy val appRoutes = wire[app.Routes]
    lazy val healthRoutes = wire[health.Routes]
    lazy val prodRoutes = wire[prod.Routes]
    lazy val testOnlyDoNotUseInAppConfRoutes = wire[testOnlyDoNotUseInAppConf.Routes]

    if (configuration.underlying.hasPath("play.http.router")) {
      configuration.getOptional[String]("play.http.router") match {
        case Some("testOnlyDoNotUseInAppConf.Routes") => testOnlyDoNotUseInAppConfRoutes
        case Some("prod.Routes")                      => prodRoutes
        case Some(other)                              => Logger.warn(s"Unrecognised router $other; using prod.Routes"); prodRoutes
        case _                                        => prodRoutes
      }
    } else {
      prodRoutes
    }
  }
}
