package uk.gov.hmrc.digitalservicestax.util


import java.io.File
import java.time.Clock

import com.softwaremill.macwire._
import play.api.Mode.Mode
import play.api.{Configuration, Environment, Play}
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.audit.DefaultAuditConnector
import uk.gov.hmrc.play.bootstrap.config.{AuditingConfigProvider, RunMode, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpAuditing

trait TestWiring {
  val appName = configuration.getString("appName").get
  lazy val auditingConfigProvider: AuditingConfigProvider = wire[AuditingConfigProvider]
  val auditingconfig = auditingConfigProvider.get()
  lazy val auditConnector: AuditConnector = wire[DefaultAuditConnector]
  lazy val httpAuditing: HttpAuditing = wire[DefaultHttpAuditing]
  lazy val configuration: Configuration = Configuration.load(environment, Map("auditing.enabled" -> "false"))
  lazy val runMode = wire[RunMode]
  lazy val environment: Environment = Environment.simple(new File("."))
  lazy val mode: Mode = environment.mode
  implicit def clock: Clock = Clock.systemDefaultZone()
  lazy val actorSystem = Play.current.actorSystem
  val servicesConfig = wire[ServicesConfig]
}
