import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

val appName = "digital-services-tax"
PlayKeys.playDefaultPort := 8741

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := "<empty>;.*views.*;.*prod.*;.*test.*;app.routes;testOnlyDoNotUseInAppConf;prod.*;uk.gov.hmrc.digitalservicestax.controllers;uk.gov.hmrc.digitalservicestax.controllers.javascript;ltbs.resilientcalls;",
    ScoverageKeys.coverageExcludedFiles := "<empty>;.*BuildInfo.*;.*Routes.*;testOnlyDoNotUseInAppConf;",
    ScoverageKeys.coverageMinimum := 80,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true
  )
}

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)
  .settings(
    majorVersion                     := 0,
    libraryDependencies              ++= AppDependencies.compile ++ AppDependencies.test ++ Seq(
      "com.softwaremill.macwire"  %% "macros"                % "2.3.4" % Test,
      "com.softwaremill.macwire"  %% "macrosakka"            % "2.3.4" % Test,
      "com.softwaremill.macwire"  %% "proxy"                 % "2.3.4" % Test,
      "com.softwaremill.macwire"  %% "util"                  % "2.3.4" % Test
    )
  )
  .settings(scoverageSettings)
  .settings(publishingSettings: _*)
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers += Resolver.jcenterRepo)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427

resolvers += Resolver.bintrayRepo("wolfendale", "maven")

scalaVersion := "2.12.11"
