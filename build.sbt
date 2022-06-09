import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, integrationTestSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

scalaVersion := "2.12.11"
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
    libraryDependencies              ++= AppDependencies.compile ++ AppDependencies.test
  )
  .settings(scoverageSettings)
  .settings(publishingSettings: _*)
  .configs(IntegrationTest)
  .settings(integrationTestSettings ++ unitTestSettings)
  .settings(resolvers ++= Seq(Resolver.jcenterRepo,  Resolver.bintrayRepo("wolfendale", "maven")))
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427

// If you want integration tests in the test package you need to extend Test config ...
lazy val IntegrationTest = config("it") extend Test

lazy val unitTestSettings = inConfig(Test)(Defaults.testTasks) ++ Seq(
  Test / testOptions := Seq(Tests.Filter(name => name startsWith "unit")),
  Test / fork := true,
  Test / unmanagedSourceDirectories := Seq((Test / baseDirectory).value / "test"),
  addTestReportOption(Test, "test-reports")
)

lazy val integrationTestSettings = inConfig(IntegrationTest)(Defaults.testTasks) ++ Seq(
  IntegrationTest / testOptions := Seq(Tests.Filter(name => name startsWith "it")),
  IntegrationTest / fork := false,
  IntegrationTest / parallelExecution := false,
  addTestReportOption(IntegrationTest, "integration-test-reports")
)