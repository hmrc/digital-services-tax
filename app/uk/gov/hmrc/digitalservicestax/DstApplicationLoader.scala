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
}

class DstComponents(context: Context) extends BasicComponents(context) { 

  private lazy val mongo = wire[services.MongoPersistence]
  private lazy val loggedInAction = wire[actions.LoggedInAction]
  private lazy val registeredAction = wire[actions.Registered]
  private lazy val appConfig = new config.AppConfig(configuration, serviceConfig)

  // connectors
  private lazy val retConnector = wire[connectors.ReturnConnector]
  private lazy val regConnector = wire[connectors.RegistrationConnector]
  private lazy val testConnector = wire[test.TestConnector]
  private lazy val emailConnector = wire[connectors.EmailConnector]
  private lazy val taxEnrolConnector = wire[connectors.TaxEnrolmentConnector]
  private lazy val rosmConnector = wire[connectors.RosmConnector]

  // controllers
  private implicit lazy val returnsController = wire[controllers.ReturnsController]
  private lazy val regController = wire[controllers.RegistrationsController]
  private lazy val testController = wire[test.TestController]
  private lazy val rosmController = wire[controllers.RosmController]
  private lazy val teC = wire[controllers.TaxEnrolmentCallbackController]

  // routing
  def router: Router = {

    val prefix: String = ""
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
