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
import com.github.ghik.silencer.silent
import com.kenshoo.play.metrics._
import com.softwaremill.macwire._
import config.DstConfig
import play.api._, ApplicationLoader.Context
import play.api.i18n._
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc._
import play.api.routing.Router
import play.modules.reactivemongo.ReactiveMongoApiFromContext
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.CorePost
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.bootstrap.audit.DefaultAuditConnector
import uk.gov.hmrc.play.bootstrap.config._
import uk.gov.hmrc.play.bootstrap.filters._
import uk.gov.hmrc.play.bootstrap.filters.microservice.DefaultMicroserviceAuditFilter
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

  val dstConfig: DstConfig = {
    import pureconfig._, generic.ProductHint, generic.auto._
    import pureconfig.configurable._
    import java.time.format.DateTimeFormatter
    @silent("never used") implicit val localDateConvert =
      localDateConfigConvert(DateTimeFormatter.ISO_DATE)
    @silent("never used") implicit def hint[T] =
      ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))
    ConfigSource.fromConfig(configuration.underlying).loadOrThrow[DstConfig]
  }

  // ----------------------------------------
  // assorted bits of hmrc 
  // ----------------------------------------
  val mode = environment.mode
  lazy val runMode = new RunMode(configuration, mode)
  lazy val auditingConfigProvider: AuditingConfigProvider =
    new AuditingConfigProvider(configuration, runMode, dstConfig.appName)
  val auditConnector = new DefaultAuditConnector(auditingConfigProvider.get())

  val auditing: HttpAuditing = new DefaultHttpAuditing(auditConnector, dstConfig.appName)
  
  lazy val authConnector: AuthConnector = new PlayAuthConnector {
    override val serviceUrl: String = dstConfig.upstreamServices.auth.baseUrl
    override val http: CorePost = httpClient
  }

  lazy val metrics: Metrics = wire[MetricsImpl]
  lazy val metricsController = new MetricsController(metrics, controllerComponents)
  lazy val healthController = new HealthController(configuration, environment, controllerComponents)

  // filters
  lazy val controllerConfigs: ControllerConfigs = wireWith(ControllerConfigs.fromConfig _)
  lazy val httpAuditEvent: HttpAuditEvent = new DefaultHttpAuditEvent(dstConfig.appName)

  override def httpFilters: Seq[EssentialFilter] = Seq(
    new MetricsFilterImpl(metrics),
    wire[DefaultMicroserviceAuditFilter],
    new DefaultLoggingFilter(controllerConfigs),
    new CacheControlFilter(wireWith(CacheControlConfig.fromConfig _), materializer),
    new MDCFilter(materializer, configuration, dstConfig.appName)
  )  
}

class DstComponents(context: Context) extends BasicComponents(context) { 

  lazy val mongo = wire[services.MongoPersistence]
  lazy val loggedInAction = wire[actions.LoggedInAction]
  lazy val registeredAction = wire[actions.Registered]

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
    @silent("never used") lazy val prefix = ""
    @silent("never used") lazy val appRoutes = wire[app.Routes]
    @silent("never used") lazy val healthRoutes = wire[health.Routes]
    lazy val prodRoutes = wire[prod.Routes]

    dstConfig.play.http.router match {
      case Some("testOnlyDoNotUseInAppConf.Routes") => wire[testOnlyDoNotUseInAppConf.Routes]
      case _                                        => prodRoutes
    }
  }
}
